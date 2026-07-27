package com.autodeploy.ui.tab;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.domain.model.Project;
import com.autodeploy.domain.model.Server;
import com.autodeploy.infrastructure.connection.ConnectionManager;
import com.autodeploy.notification.NotificationController;
import com.autodeploy.service.deploy.BuildService;
import com.autodeploy.service.deploy.FileUploadService;
import com.autodeploy.service.restart.RestartService;
import com.autodeploy.service.scanner.FileScannerService;
import com.autodeploy.service.utility.BrowserService;
import com.autodeploy.service.utility.FileOpener;
import com.autodeploy.service.utility.LogDownloadService;
import com.autodeploy.ui.dialog.CustomAlert;
import com.autodeploy.ui.overlay.UIOverlayManager;
import com.autodeploy.ui.window.component.*;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Tab de deployment activ pentru o combinație Project + Server.
 * <p>
 * Conținutul și logica sunt identice cu {@link com.autodeploy.ui.window.DeploymentWindow},
 * minus title bar-ul propriu (acum shared în {@link com.autodeploy.ui.window.MainWindow}).
 * <p>
 * Lifecycle-ul inițializării are 3 faze (identic cu DeploymentWindow):
 * <ol>
 *   <li><b>Constructor</b> → FXML load → creare StackPane wrapper + overlayManager</li>
 *   <li><b>{@link #initialize}</b> (apelat de FXMLLoader) — creează componentele UI
 *       care NU depind de overlay/conexiune</li>
 *   <li><b>{@link #lateInit}</b> — creează componentele care DEPIND de overlayManager
 *       și inițiază conexiunea</li>
 * </ol>
 * <p>
 * {@link UIOverlayManager} wrappează StackPane-ul propriu al tab-ului — overlay-urile
 * (blur, loading, reconnect) afectează doar conținutul acestui tab, nu alte tab-uri.
 */
public class DeploymentSessionTab extends SessionTab implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(DeploymentSessionTab.class.getName());

    @FXML private Button toggleLogBtn;
    @FXML private Label projectNameLabel, serverNameLabel, jarCountLabel, jspCountLabel;
    @FXML private MFXButton changeBtn, restartServerBtn, downloadLogsBtn, buildProjectBtn;
    @FXML private MFXButton openBrowserBtn, uploadJarsBtn, uploadJspsBtn, uploadAllBtn;
    @FXML private TitledPane jarSection, jspSection;
    @FXML private VBox jarListContainer, jspListContainer, logSection;
    @FXML private TextField jspSearchField;
    @FXML private TextArea logArea;

    private final Project project;
    private final Server server;
    private final SessionTabManager tabManager;
    private final Stage ownerStage;

    // --- Componente delegate ---
    private LogPanelManager logPanel;
    private DeploymentActionBar actionBar;
    private ConnectionHandler connectionHandler;
    private UploadHandler uploadHandler;
    private RestartHandler restartHandler;
    private UIOverlayManager overlayManager;

    // --- Servicii ---
    private FileScannerService fileScannerService;
    private FileListPanel jarPanel;
    private FileListPanel jspPanel;
    private BuildService buildService;
    private LogDownloadService logDownloadService;
    private BrowserService browserService;
    private FileOpener fileOpener;

    /**
     * Faza 1: Încarcă FXML-ul de conținut, creează StackPane-ul wrapper pentru overlay-uri
     * și declanșează lateInit().
     * <p>
     * Structura: contentRoot (StackPane) → [contentVBox (din FXML), overlay-uri]
     */
    public DeploymentSessionTab(Project project, Server server,
                                 SessionTabManager tabManager, Stage ownerStage) {
        this.project = project;
        this.server = server;
        this.tabManager = tabManager;
        this.ownerStage = ownerStage;
        setDisplayName(project.getName() + " @ " + server.getName());

        try {
            VBox contentVBox = (VBox) Assets.loadFxml("/fxml/deployment-tab-content.fxml", this);
            StackPane contentStackPane = new StackPane(contentVBox);
            contentRoot = contentStackPane;
            this.overlayManager = new UIOverlayManager(contentStackPane, contentVBox);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load deployment tab content", e);
            throw new RuntimeException("Failed to load deployment tab content", e);
        }

        lateInit();
    }

    /**
     * Faza 2 (apelată de FXMLLoader, ÎNAINTE de finalul constructorului).
     * Creează componentele UI care NU depind de overlayManager sau conexiune.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logPanel = new LogPanelManager(logArea, logSection, toggleLogBtn);
        logPanel.setup();

        fileOpener = new FileOpener(logPanel::log);

        actionBar = new DeploymentActionBar(
                restartServerBtn, downloadLogsBtn, buildProjectBtn,
                openBrowserBtn, uploadJarsBtn, uploadJspsBtn, uploadAllBtn
        );

        buildService = new BuildService(project, logPanel::log);
        browserService = new BrowserService(project, logPanel::log);
        fileScannerService = new FileScannerService(project, logPanel::log);

        jarPanel = new FileListPanel(jarListContainer, jarCountLabel,
                MSG_NO_JAR_FILES, logPanel::log);
        jspPanel = new FileListPanel(jspListContainer, jspCountLabel,
                MSG_NO_JSP_FILES, logPanel::log);

        setupHeader();
        setupFileScanner();
        setupButtons();

        jarSection.setExpanded(false);
        jspSection.setExpanded(false);
        actionBar.setAllDisabled(true);

        logPanel.log("✓ Deployment tab initialized");
        logPanel.log("Project: " + project.getName());
        logPanel.log("Server: " + server.getName() + " (" + server.getHost() + ")");
    }

    /**
     * Faza 3: Creează componentele care depind de overlayManager și inițiază conexiunea.
     * Lanțul de dependențe: overlayManager → connectionHandler → uploadHandler/restartHandler.
     */
    private void lateInit() {
        ConnectionManager connectionManager = new ConnectionManager(server, logPanel::log);

        FileUploadService fileUploadService = new FileUploadService(
                project, connectionManager, logPanel::log);
        logDownloadService = new LogDownloadService(server, connectionManager, logPanel::log);
        RestartService restartService = new RestartService(server, connectionManager, logPanel::log);

        connectionHandler = new ConnectionHandler(
                server, connectionManager, overlayManager, logPanel::log);
        connectionHandler.setRestartService(restartService);
        connectionHandler.setOnConnected(() -> actionBar.setAllDisabled(false));
        connectionHandler.setOnDisconnected(() -> actionBar.setAllDisabled(true));
        connectionHandler.setOnReturnToSelection(this::performCleanupAndReturnToSelection);
        connectionHandler.setupCallbacks();

        logPanel.setConnectionErrorCallback(msg -> {
            if (connectionHandler.isConnected()) {
                connectionHandler.notifyConnectionLost();
            }
        });

        uploadHandler = new UploadHandler(
                jarPanel, jspPanel, fileUploadService,
                connectionHandler, actionBar, logPanel::log);

        restartHandler = new RestartHandler(
                restartService, restartServerBtn, overlayManager, ownerStage,
                logPanel::log,
                server.getName() + " (" + server.getHost() + ")",
                project.getName());
        restartHandler.setupCallbacks();

        connectionHandler.connect();
    }

    private void setupHeader() {
        projectNameLabel.setText(project.getName());
        serverNameLabel.setText(server.getName() + " (" + server.getHost() + ")");
    }

    private void setupFileScanner() {
        jarPanel.loadFiles(fileScannerService.scanJarFiles());
        jspPanel.loadFiles(fileScannerService.scanJspFiles());

        fileScannerService.startJarWatcher(jarPanel::handleFileChange);
        fileScannerService.startJspWatcher(jspPanel::handleFileChange);

        jspSearchField.textProperty().addListener((obs, oldVal, newVal) ->
                jspPanel.filter(newVal));
    }

    private void setupButtons() {
        restartServerBtn.setOnAction(e -> restartHandler.handleRestart());
        downloadLogsBtn.setOnAction(e -> handleDownloadLogs());
        buildProjectBtn.setOnAction(e -> handleBuildProject());
        openBrowserBtn.setOnAction(e -> handleOpenBrowser());
        uploadJarsBtn.setOnAction(e -> uploadHandler.uploadJars());
        uploadJspsBtn.setOnAction(e -> uploadHandler.uploadJsps());
        uploadAllBtn.setOnAction(e -> uploadHandler.uploadAll());
        changeBtn.setOnAction(e -> returnToSelection());
    }

    private void handleBuildProject() {
        var validation = buildService.validateConfiguration();
        if (!validation.isSuccess()) {
            CustomAlert.showError("Build Configuration Missing", validation.getErrorMessage());
            return;
        }

        actionBar.getBuildProjectBtn().setDisable(true);
        var buildTask = buildService.buildAsync();

        buildTask.setOnSucceeded(event -> {
            actionBar.getBuildProjectBtn().setDisable(false);
            if (!buildTask.getValue().isSuccess()) {
                CustomAlert.showError("Build Failed", buildTask.getValue().getErrorMessage());
            }
        });

        buildTask.setOnFailed(event -> {
            actionBar.getBuildProjectBtn().setDisable(false);
            CustomAlert.showError("Build Failed", buildTask.getException().getMessage());
        });

        AsyncHelper.runDaemon(buildTask, "Ant-Build");
    }

    private void handleDownloadLogs() {
        if (!logDownloadService.validateConfiguration().isSuccess()) {
            CustomAlert.showError("Configuration Missing",
                    logDownloadService.validateConfiguration().getErrorMessage());
            return;
        }
        if (!connectionHandler.isConnected()) {
            CustomAlert.showError("Connection Error", MSG_NOT_CONNECTED);
            return;
        }

        actionBar.getDownloadLogsBtn().setDisable(true);
        var downloadTask = logDownloadService.downloadAsync();

        downloadTask.setOnSucceeded(event -> {
            actionBar.getDownloadLogsBtn().setDisable(false);
            var result = downloadTask.getValue();
            if (result.isSuccess()) {
                showDownloadNotification(result.getDownloadedFile());
            } else {
                CustomAlert.showError("Download Failed", result.getErrorMessage());
            }
        });

        downloadTask.setOnFailed(event -> {
            actionBar.getDownloadLogsBtn().setDisable(false);
            CustomAlert.showError("Download Failed", downloadTask.getException().getMessage());
        });

        AsyncHelper.runDaemon(downloadTask, "Log-Download");
    }

    private void showDownloadNotification(File downloadedFile) {
        new NotificationController().showDownloadSuccessNotification(
                downloadedFile.getName(), () -> {
                    logPanel.log("🖱 User clicked 'Open With' button");
                    if (!fileOpener.openWithDialog(downloadedFile)
                            && !fileOpener.openContainingFolder(downloadedFile)) {
                        CustomAlert.showError("Open Failed",
                                "Could not open file or folder.\n" + downloadedFile.getAbsolutePath());
                    }
                });
    }

    private void handleOpenBrowser() {
        if (!browserService.openServer(server)) {
            String url = Project.getFullBrowserUrl(server.getHost());
            CustomAlert.showError("Browser Error",
                    "Failed to open browser.\nPlease open manually:\n" + url);
        }
    }

    /**
     * Confirmare + cleanup + deschidere tab selecție nou.
     * Overlay-ul de blur e afișat în spatele dialogului de confirmare.
     */
    private void returnToSelection() {
        logPanel.log("🔄 Requesting return to selection...");
        overlayManager.showSimpleBlur();

        boolean confirmed = CustomAlert.showConfirmation(
                ownerStage, "Change Project/Server",
                "This will disconnect from the server and return to the selection tab.\n\nAre you sure?"
        );

        overlayManager.hideOverlay();
        if (confirmed) {
            logPanel.log("✓ User confirmed return to selection");
            performCleanupAndReturnToSelection();
        } else {
            logPanel.log("⚠ Return to selection cancelled by user");
        }
    }

    /**
     * Cleanup complet și deschide un nou tab de selecție.
     * Înlocuiește {@code performCleanupAndReturn()} din DeploymentWindow — în loc de
     * a deschide o nouă fereastră, deschide un nou tab în fereastra principală.
     */
    private void performCleanupAndReturnToSelection() {
        logPanel.log("✓ Performing cleanup...");
        cleanupResources();
        logPanel.log("✓ Cleanup completed");

        Platform.runLater(() -> {
            tabManager.openSelectionTab();
            tabManager.closeTab(this);
        });
    }

    /**
     * Curăță resursele: restart polling → file watchers → conexiune SFTP.
     * Ordinea contează: restart (oprește polling) → watchers → conexiune (ultima).
     * Null-check-urile previn double-cleanup dacă e apelat de două ori.
     */
    private void cleanupResources() {
        if (restartHandler != null) restartHandler.shutdown();
        if (fileScannerService != null) fileScannerService.stopWatchers();
        if (connectionHandler != null) connectionHandler.disconnect();
    }

    @Override
    public void close() {
        cleanupResources();
    }
}
