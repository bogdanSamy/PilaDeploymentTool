package com.autodeploy.ui.window;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.core.config.ApplicationConfig;
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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import xss.it.nfx.NfxStage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Fereastra principală de deployment — orchestrează toate componentele aplicației.
 * <p>
 * Lifecycle-ul inițializării are 3 faze (ordine importantă):
 * <ol>
 *   <li><b>Constructor</b> → {@link #initializeWindow()} — creează scena, rootPane,
 *       contentPane, overlayManager</li>
 *   <li><b>{@link .initialize}</b> (apelat de FXMLLoader) — creează componentele UI
 *       care NU depind de overlay/conexiune: logPanel, titleBar, fileScanner, butoane</li>
 *   <li><b>{@link #lateInit}</b> (apelat la finalul initializeWindow) — creează
 *       componentele care DEPIND de overlayManager: connectionHandler, uploadHandler,
 *       restartHandler, și inițiază conexiunea</li>
 * </ol>
 * <p>
 * Separarea în 3 faze e necesară deoarece:
 * <ul>
 *   <li>overlayManager necesită rootPane (creat în constructor, DUPĂ FXML load)</li>
 *   <li>connectionHandler/uploadHandler necesită overlayManager</li>
 *   <li>{@code initialize()} e apelat de FXMLLoader ÎNAINTE ca constructorul să termine</li>
 * </ul>
 * <p>
 * Componentele complexe sunt delegate (vezi {@code ui/window/component/}):
 * <ul>
 *   <li>{@link ConnectionHandler} — ciclul de viață al conexiunii + overlay-uri</li>
 *   <li>{@link UploadHandler} — validare + upload JAR/JSP</li>
 *   <li>{@link RestartHandler} — buton restart + timer</li>
 *   <li>{@link LogPanelManager} — log panel + detecție erori conexiune</li>
 *   <li>{@link DeploymentActionBar} — enable/disable butoane centralizat</li>
 *   <li>{@link FileListPanel} — liste de fișiere cu checkbox-uri (×2: JAR + JSP)</li>
 * </ul>
 */
public class DeploymentWindow extends NfxStage implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(DeploymentWindow.class.getName());

    @FXML private Button closeBtn, maxBtn, minBtn, toggleLogBtn;
    @FXML private SVGPath maxShape;
    @FXML private ImageView iconView;
    @FXML private Label title, projectNameLabel, serverNameLabel, jarCountLabel, jspCountLabel;
    @FXML private MFXButton changeBtn, restartServerBtn, downloadLogsBtn, buildProjectBtn;
    @FXML private MFXButton openBrowserBtn, uploadJarsBtn, uploadJspsBtn, uploadAllBtn;
    @FXML private TitledPane jarSection, jspSection;
    @FXML private VBox jarListContainer, jspListContainer, logSection;
    @FXML private TextField jspSearchField;
    @FXML private TextArea logArea;
    private StackPane rootPane;
    private VBox contentPane;

    private final Project project;
    private final Server server;
    private SelectionWindow selectionWindow;

    // --- Componente delegate ---

    private TitleBarManager titleBarManager;
    private LogPanelManager logPanel;
    private DeploymentActionBar actionBar;
    private ConnectionHandler connectionHandler;
    private UploadHandler uploadHandler;
    private RestartHandler restartHandler;
    private UIOverlayManager overlayManager;

    // --- Servicii (folosite direct, fără wrapper component) ---
    private FileScannerService fileScannerService;
    private FileListPanel jarPanel;
    private FileListPanel jspPanel;
    private BuildService buildService;
    private LogDownloadService logDownloadService;
    private BrowserService browserService;
    private FileOpener fileOpener;

    public DeploymentWindow(Project project, Server server) {
        super();
        this.project = project;
        this.server = server;

        try {
            initializeWindow();
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize DeploymentWindow: " + e.getMessage());
            throw new RuntimeException("Failed to initialize DeploymentWindow", e);
        }
    }

    /**
     * Faza 1: Creează scena cu StackPane root (necesar pentru overlay-uri).
     * <p>
     * Structura vizuală: rootPane (StackPane) → [contentPane (VBox din FXML), overlay]
     * StackPane-ul permite suprapunerea overlay-urilor peste conținut.
     * <p>
     * Ordinea: FXML load (care triggerează initialize()) → creare rootPane →
     * creare overlayManager → lateInit (componente dependente de overlay).
     */
    private void initializeWindow() throws IOException {
        getIcons().add(new Image(Assets.location("/logo.png").toExternalForm()));
        Parent parent = Assets.loadFxml("/fxml/deployment-window.fxml", this);

        contentPane = (VBox) parent;
        rootPane = new StackPane(contentPane);

        setScene(new Scene(rootPane));
        setTitle(WINDOW_TITLE_PREFIX + project.getName());
        setOnCloseRequest(event -> handleWindowClose());

        this.overlayManager = new UIOverlayManager(rootPane, contentPane);

        lateInit();
    }

    /**
     * Faza 2 (apelată de FXMLLoader, ÎNAINTE de finalul constructorului).
     * Creează componentele UI care NU depind de overlayManager sau conexiune.
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logPanel = new LogPanelManager(logArea, logSection, toggleLogBtn);
        logPanel.setup();

        fileOpener = new FileOpener(logPanel::log);

        actionBar = new DeploymentActionBar(
                restartServerBtn, downloadLogsBtn, buildProjectBtn,
                openBrowserBtn, uploadJarsBtn, uploadJspsBtn, uploadAllBtn
        );

        titleBarManager = new TitleBarManager(
                this, closeBtn, maxBtn, minBtn, maxShape, iconView, title,
                (close, max, min) -> {
                    setCloseControl(close);
                    setMaxControl(max);
                    setMinControl(min);
                }
        );
        titleBarManager.setup();

        buildService = new BuildService(project, logPanel::log);
        browserService = new BrowserService(logPanel::log);
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

        logPanel.log("✓ Deployment window initialized");
        logPanel.log("Project: " + project.getName());
        logPanel.log("Server: " + server.getName() + " (" + server.getHost() + ")");
    }

    /**
     * Faza 3: Creează componentele care depind de overlayManager și conexiune.
     * <p>
     * Lanțul de dependențe:
     * <pre>
     * overlayManager ─┬→ connectionHandler ─┬→ uploadHandler
     *                 │                      └→ restartHandler
     *                 └→ restartHandler
     * </pre>
     * La final, inițiază conexiunea — afișează overlay-ul de loading.
     */
    private void lateInit() {
        ConnectionManager connectionManager = new ConnectionManager(server, logPanel::log);

        FileUploadService fileUploadService = new FileUploadService(
                project, connectionManager, logPanel::log);
        logDownloadService = new LogDownloadService(
                connectionManager, logPanel::log);
        RestartService restartService = new RestartService(
                server, connectionManager, logPanel::log);

        connectionHandler = new ConnectionHandler(
                server, connectionManager, overlayManager, logPanel::log);
        connectionHandler.setRestartService(restartService);
        connectionHandler.setOnConnected(() -> actionBar.setAllDisabled(false));
        connectionHandler.setOnDisconnected(() -> actionBar.setAllDisabled(true));
        connectionHandler.setOnReturnToSelection(this::performCleanupAndReturn);
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
                restartService, restartServerBtn, overlayManager, this,
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
        changeBtn.setOnAction(e -> returnToSelectionWindow());
    }

    /**
     * Build asincron: validare → disable buton → build pe thread daemon → re-enable.
     */
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

    /**
     * Download log asincron: validare → verificare conexiune → download → notificare.
     */
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

    /**
     * Notificare toast la download reușit cu buton "Open With...".
     * Fallback: dacă "Open With" eșuează, încearcă deschiderea folderului.
     */
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
            String url = ApplicationConfig.getInstance().getFullBrowserUrl(server.getHost());
            CustomAlert.showError("Browser Error",
                    "Failed to open browser.\nPlease open manually:\n" + url);
        }
    }

    /**
     * Confirmare + cleanup + revenire la SelectionWindow.
     * Overlay-ul de blur e afișat în spatele dialogului de confirmare.
     */
    private void returnToSelectionWindow() {
        logPanel.log("🔄 Requesting return to selection window...");
        overlayManager.showSimpleBlur();

        boolean confirmed = CustomAlert.showConfirmation(
                this, "Change Project/Server",
                "This will disconnect from the server and return to the selection window.\n\nAre you sure?"
        );

        overlayManager.hideOverlay();
        if (confirmed) {
            logPanel.log("✓ User confirmed return to selection");
            performCleanupAndReturn();
        } else {
            logPanel.log("⚠ Return to selection cancelled by user");
        }
    }

    /**
     * Cleanup complet și navigare înapoi la SelectionWindow.
     * Refolosește instanța existentă dacă există, altfel creează una nouă.
     */
    private void performCleanupAndReturn() {
        logPanel.log("✓ Performing cleanup...");
        cleanupResources();
        logPanel.log("✓ Cleanup completed");

        Platform.runLater(() -> {
            if (selectionWindow != null) {
                selectionWindow.show();
            } else {
                try {
                    new SelectionWindow().show();
                } catch (Exception e) {
                    LOGGER.severe("Error creating selection window: " + e.getMessage());
                }
            }
        });
        close();
    }

    private void handleWindowClose() {
        logPanel.log("🔌 Application closing...");
        cleanupResources();
    }

    /**
     * Oprește toate resursele: restart polling, file watchers, conexiune SFTP.
     * Ordinea contează: restart (oprește polling) → watchers → conexiune (ultima).
     */
    private void cleanupResources() {
        if (restartHandler != null) restartHandler.shutdown();
        if (fileScannerService != null) fileScannerService.stopWatchers();
        if (connectionHandler != null) connectionHandler.disconnect();
    }

    @Override
    protected double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }

    public void setSelectionWindow(SelectionWindow selectionWindow) {
        this.selectionWindow = selectionWindow;
    }
}