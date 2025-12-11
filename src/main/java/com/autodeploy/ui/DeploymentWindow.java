package com.autodeploy.ui;

import com.autodeploy.assets.Assets;
import com.autodeploy.model.Project;
import com.autodeploy.model.Server;
import com.autodeploy.services.*;
import com.autodeploy.ui.dialogs.CustomAlert;
import com.autodeploy.ui.dialogs.NotificationController;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
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
import javafx.util.Duration;
import xss.it.nfx.NfxStage;
import xss.it.nfx.WindowState;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static com.autodeploy.constants.DeploymentConstants.*;

public class DeploymentWindow extends NfxStage implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(DeploymentWindow.class.getName());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // UI Controls
    @FXML private Button closeBtn, maxBtn, minBtn, toggleLogBtn;
    @FXML private SVGPath maxShape;
    @FXML private ImageView iconView;
    @FXML private Label title, projectNameLabel, serverNameLabel, jarCountLabel, jspCountLabel;
    @FXML private MFXButton changeBtn, restartServerBtn, downloadLogsBtn, buildProjectBtn, openBrowserBtn, uploadJarsBtn, uploadJspsBtn, uploadAllBtn;
    @FXML private TitledPane jarSection, jspSection;
    @FXML private VBox jarListContainer, jspListContainer, logSection;
    @FXML private TextField jspSearchField;
    @FXML private TextArea logArea;

    // Core Data
    private final Project project;
    private final Server server;

    // UI State
    private SelectionWindow selectionWindow;
    private StackPane rootPane;
    private VBox contentPane;
    private UIOverlayManager overlayManager;
    private boolean logVisible = false;
    private boolean isConnecting = false;

    // Services
    private ConnectionManager connectionManager;
    private FileScannerService fileScannerService;
    private FileUploadService fileUploadService;
    private BuildService buildService;
    private LogDownloadService logDownloadService;
    private RestartService restartService;
    private BrowserService browserService;

    // Timers
    private Timeline restartTimerTimeline;

    // =================================================================================================================
    // INITIALIZATION & SETUP
    // =================================================================================================================

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

    private void initializeWindow() throws IOException {
        getIcons().add(new Image(Assets.load("/logo.png").toExternalForm()));
        Parent parent = Assets.load("/fxml/deployment-window.fxml", this);

        contentPane = (VBox) parent;
        rootPane = new StackPane();
        rootPane.getChildren().add(contentPane);

        Scene scene = new Scene(rootPane);
        setScene(scene);
        setTitle(WINDOW_TITLE_PREFIX + project.getName());

        setOnCloseRequest(event -> handleWindowClose());

        // Initialize OverlayManager immediately after scene setup
        this.overlayManager = new UIOverlayManager(rootPane, contentPane);

        // Auto-connect
        connectToServer();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTitleBar();
        setupHeader();
        setupLogArea();

        initializeServices();

        setupUI();
        setupButtons();

        jarSection.setExpanded(false);
        jspSection.setExpanded(false);
        disableActionButtons(true);

        log("✓ Deployment window initialized");
        log("Project: " + project.getName());
        log("Server: " + server.getName() + " (" + server.getHost() + ")");
    }

    private void initializeServices() {

        connectionManager = new ConnectionManager(server, this::log);
        fileScannerService = new FileScannerService(project, this::log);
        buildService = new BuildService(project, this::log);
        browserService = new BrowserService(this::log);

        fileUploadService = new FileUploadService(project, connectionManager.getSftpManager(), this::log);
        logDownloadService = new LogDownloadService(connectionManager.getSftpManager(), this::log);
        restartService = new RestartService(server, connectionManager.getSftpManager(), this::log);

        setupConnectionCallbacks();
        setupRestartCallbacks();
    }

    private void setupUI() {
        fileScannerService.initializeUI(jarListContainer, jspListContainer, jarCountLabel, jspCountLabel);
        fileScannerService.setupJarSection();
        fileScannerService.setupJspSection();
        fileScannerService.startWatchers();

        jspSearchField.textProperty().addListener((obs, oldVal, newVal) ->
                fileScannerService.filterJspFiles(newVal));
    }

    private void setupButtons() {
        restartServerBtn.setOnAction(e -> handleRestartServer());
        downloadLogsBtn.setOnAction(e -> handleDownloadLogs());
        buildProjectBtn.setOnAction(e -> handleBuildProject());
        openBrowserBtn.setOnAction(e -> handleOpenBrowser());
        uploadJarsBtn.setOnAction(e -> handleUploadJars());
        uploadJspsBtn.setOnAction(e -> handleUploadJsps());
        uploadAllBtn.setOnAction(e -> handleUploadAll());
        toggleLogBtn.setOnAction(e -> toggleLogVisibility());
        changeBtn.setOnAction(e -> returnToSelectionWindow());
    }

    private void setupTitleBar() {
        getIcons().addListener((ListChangeListener<? super Image>) observable -> {
            if (!getIcons().isEmpty()) iconView.setImage(getIcons().get(0));
        });
        titleProperty().addListener((observable, oldValue, newValue) -> title.setText(newValue));

        setCloseControl(closeBtn);
        setMaxControl(maxBtn);
        setMinControl(minBtn);

        handleMaxStateChangeShape(getWindowState());
        windowStateProperty().addListener((obs, oldState, newState) -> handleMaxStateChangeShape(newState));
    }

    private void setupHeader() {
        projectNameLabel.setText(project.getName());
        serverNameLabel.setText(server.getName() + " (" + server.getHost() + ")");
    }

    private void setupLogArea() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
    }

    // =================================================================================================================
    // CONNECTION LIFECYCLE & CALLBACKS
    // =================================================================================================================

    public void connectToServer() {
        isConnecting = true;
        overlayManager.showLoadingOverlay("Connecting to Server...",
                server.getName() + " (" + server.getHost() + ")");
        disableActionButtons(true);

        var connectionTask = connectionManager.connectAsync();
        Thread connectionThread = new Thread(connectionTask, "SFTP-Connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private void setupConnectionCallbacks() {
        connectionManager.setOnConnectionEstablished(() -> {
            if (isConnecting) {
                overlayManager.hideOverlay();
                isConnecting = false;
            }
            disableActionButtons(false);
            if (restartService.initialize()) restartService.startPolling();
        });

        connectionManager.setOnConnectionLost(() -> {
            if (restartService != null) restartService.stopPolling();
            if (!isConnecting) {
                disableActionButtons(true);
                showReconnectOverlay();
            }
        });

        connectionManager.setOnReconnectStarted(() -> {
            isConnecting = true;
            overlayManager.showLoadingOverlay("Reconnecting to Server...",
                    server.getName() + " (" + server.getHost() + ")");
        });

        connectionManager.setOnConnectionFailed(errorMessage -> {
            isConnecting = false;
            overlayManager.showReconnectFailure(
                    "Connection Failed",
                    errorMessage,
                    this::connectToServer,
                    this::performCleanupAndReturn
            );
        });
    }

    private void showReconnectOverlay() {
        overlayManager.showReconnectOverlay(
                server.getName(),
                server.getHost(),
                this::handleReconnect,
                this::performCleanupAndReturn
        );
    }

    private void handleReconnect() {
        log("🔄 Attempting to reconnect...");
        isConnecting = true;
        overlayManager.showLoadingOverlay("Reconnecting...", "Attempting to reach " + server.getHost());

        var reconnectTask = connectionManager.reconnectAsync();
        reconnectTask.setOnFailed(event -> {
            isConnecting = false;
            String errorMsg = reconnectTask.getException() != null ?
                    reconnectTask.getException().getMessage() : "Unknown Error";

            overlayManager.showReconnectFailure(
                    "Reconnection Failed",
                    errorMsg,
                    this::handleReconnect,
                    this::performCleanupAndReturn
            );
        });

        Thread thread = new Thread(reconnectTask, "SFTP-Reconnect");
        thread.setDaemon(true);
        thread.start();
    }

    // =================================================================================================================
    // NAVIGATION & CLEANUP
    // =================================================================================================================

    private void returnToSelectionWindow() {
        log("🔄 Requesting return to selection window...");
        overlayManager.showSimpleBlur();

        boolean confirmed = CustomAlert.showConfirmation(
                this,
                "Change Project/Server",
                "This will disconnect from the server and return to the selection window.\n\nAre you sure?"
        );

        overlayManager.hideOverlay();
        if (confirmed) {
            log("✓ User confirmed return to selection");
            performCleanupAndReturn();
        } else {
            log("⚠ Return to selection cancelled by user");
        }
    }

    private void performCleanupAndReturn() {
        log("✓ Performing cleanup...");
        cleanupResources();
        log("✓ Cleanup completed");

        if (selectionWindow != null) {
            Platform.runLater(() -> selectionWindow.show());
        } else {
            Platform.runLater(() -> {
                try {
                    new SelectionWindow().show();
                } catch (Exception e) {
                    LOGGER.severe("Error creating selection window: " + e.getMessage());
                }
            });
        }
        close();
    }

    private void handleWindowClose() {
        log("🔌 Application closing...");
        cleanupResources();
    }

    private void cleanupResources() {
        if (restartService != null) restartService.shutdown();
        if (fileScannerService != null) fileScannerService.stopWatchers();

        if (connectionManager != null) {
            // Remove listener to prevent 'Connection Lost' loop during intentional disconnect
            connectionManager.setOnConnectionLost(null);
            connectionManager.disconnect();
        }
    }

    // =================================================================================================================
    // FEATURE: RESTART SERVER
    // =================================================================================================================

    private void handleRestartServer() {
        log("🔄 Restart server requested...");
        overlayManager.showSimpleBlur();

        boolean confirmed = CustomAlert.showConfirmation(
                this,
                "Restart " + server.getName() + " (" + server.getHost() + ")?",
                "Requesting will notify users.\n\n" +
                        "Auto-executes in 30s unless rejected."
        );

        overlayManager.hideOverlay();

        if (confirmed) {
            log("✓ User confirmed server restart request");
            executeRestartRequest();
        } else {
            log("⚠ Server restart request cancelled by user");
        }
    }

    private void executeRestartRequest() {
        var restartTask = restartService.requestRestartAsync(project.getName());
        restartTask.setOnFailed(event -> {
            Throwable error = restartTask.getException();
            log("✗ Failed to send restart request: " + error.getMessage());
            CustomAlert.showError("Restart Request Failed", error.getMessage());
        });

        Thread thread = new Thread(restartTask, "Restart-Request");
        thread.setDaemon(true);
        thread.start();
    }

    private void setupRestartCallbacks() {
        restartService.addStatusListener(status -> Platform.runLater(() -> {
            if (status == null || status.getStatus() == null) return;
            String currentStatus = status.getStatus().toLowerCase().trim();

            if ("executing".equals(currentStatus)) {
                startRestartTimerAnimation();
            } else if ("completed".equals(currentStatus) || "idle".equals(currentStatus) || "rejected".equals(currentStatus)) {
                stopRestartTimerAnimation();
            }
        }));

        restartService.setOnButtonStateChanged(disabled -> restartServerBtn.setDisable(disabled));
    }

    private void startRestartTimerAnimation() {
        if (restartTimerTimeline != null) return;

        restartTimerTimeline = new Timeline(new KeyFrame(Duration.millis(TIMER_UPDATE_INTERVAL_MS), event ->
                restartServerBtn.setText("🔄 Restarting " + restartService.getFormattedElapsedTime())
        ));
        restartTimerTimeline.setCycleCount(Timeline.INDEFINITE);
        restartTimerTimeline.play();
    }

    private void stopRestartTimerAnimation() {
        if (restartTimerTimeline != null) {
            restartTimerTimeline.stop();
            restartTimerTimeline = null;
        }
        restartServerBtn.setText(restartService.getOriginalButtonText());
    }

    // =================================================================================================================
    // FEATURE: FILE UPLOAD & BUILD
    // =================================================================================================================

    private void handleBuildProject() {
        var validation = buildService.validateConfiguration();
        if (!validation.isSuccess()) {
            CustomAlert.showError("Build Configuration Missing", validation.getErrorMessage());
            return;
        }

        buildProjectBtn.setDisable(true);
        var buildTask = buildService.buildAsync();

        buildTask.setOnSucceeded(event -> {
            buildProjectBtn.setDisable(false);
            if (!buildTask.getValue().isSuccess()) {
                CustomAlert.showError("Build Failed", buildTask.getValue().getErrorMessage());
            }
        });

        buildTask.setOnFailed(event -> {
            buildProjectBtn.setDisable(false);
            CustomAlert.showError("Build Failed", buildTask.getException().getMessage());
        });

        Thread thread = new Thread(buildTask, "Ant-Build");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleUploadAll() {
        var jarMap = fileScannerService.getJarCheckBoxMap();
        var jspMap = fileScannerService.getJspCheckBoxMap();

        long jarCount = jarMap.values().stream().filter(CheckBox::isSelected).count();
        long jspCount = jspMap.values().stream().filter(CheckBox::isSelected).count();

        if (jarCount == 0 && jspCount == 0) {
            log("⚠ No files selected");
            CustomAlert.showWarning("No Files Selected", MSG_NO_FILES_SELECTED);
            return;
        }

        if (!checkConnection()) return;

        log("📤 Starting upload of all selected files...");
        disableUploadButtons(true);

        Thread uploadThread = new Thread(() -> {
            if (jarCount > 0) {
                Platform.runLater(() -> log("### Uploading JARs ###"));
                var jarResult = fileUploadService.uploadJars(jarMap);
                if (jarResult.isConnectionLost()) {
                    Platform.runLater(() -> {
                        log("⚠ Skipping JSP upload due to connection loss");
                        disableUploadButtons(false);
                    });
                    return;
                }
            }

            if (jspCount > 0) {
                Platform.runLater(() -> log("### Uploading JSPs ###"));
                fileUploadService.uploadJsps(jspMap);
            }

            Platform.runLater(() -> {
                log("####################################");
                log("✓ All uploads completed");
                disableUploadButtons(false);
            });
        }, "Upload-All");

        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    private void handleUploadJars() {
        var selectedJars = fileScannerService.getJarCheckBoxMap();
        if (selectedJars.values().stream().noneMatch(CheckBox::isSelected)) {
            CustomAlert.showWarning("No Files Selected", MSG_NO_FILES_SELECTED);
            return;
        }
        if (!checkConnection()) return;

        disableUploadButtons(true);
        Thread uploadThread = new Thread(() -> {
            fileUploadService.uploadJars(selectedJars);
            Platform.runLater(() -> disableUploadButtons(false));
        }, "JAR-Upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    private void handleUploadJsps() {
        var selectedJsps = fileScannerService.getJspCheckBoxMap();
        if (selectedJsps.values().stream().noneMatch(CheckBox::isSelected)) {
            CustomAlert.showWarning("No Files Selected", MSG_NO_FILES_SELECTED);
            return;
        }
        if (!checkConnection()) return;

        disableUploadButtons(true);
        Thread uploadThread = new Thread(() -> {
            fileUploadService.uploadJsps(selectedJsps);
            Platform.runLater(() -> disableUploadButtons(false));
        }, "JSP-Upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    // =================================================================================================================
    // FEATURE: LOGS & BROWSER
    // =================================================================================================================

    private void handleDownloadLogs() {
        if (!logDownloadService.validateConfiguration().isSuccess()) {
            CustomAlert.showError("Configuration Missing", logDownloadService.validateConfiguration().getErrorMessage());
            return;
        }
        if (!checkConnection()) return;

        downloadLogsBtn.setDisable(true);
        var downloadTask = logDownloadService.downloadAsync();

        downloadTask.setOnSucceeded(event -> {
            downloadLogsBtn.setDisable(false);
            var result = downloadTask.getValue();
            if (result.isSuccess()) {
                showDownloadSuccessNotification(result.getDownloadedFile());
            } else {
                CustomAlert.showError("Download Failed", result.getErrorMessage());
            }
        });

        downloadTask.setOnFailed(event -> {
            downloadLogsBtn.setDisable(false);
            CustomAlert.showError("Download Failed", downloadTask.getException().getMessage());
        });

        Thread thread = new Thread(downloadTask, "Log-Download");
        thread.setDaemon(true);
        thread.start();
    }

    private void showDownloadSuccessNotification(java.io.File downloadedFile) {
        new NotificationController().showDownloadSuccessNotification(downloadedFile.getName(), () -> {
            log("🖱 User clicked 'Open With' button");
            if (!logDownloadService.openWithDialog(downloadedFile) && !logDownloadService.openContainingFolder(downloadedFile)) {
                CustomAlert.showError("Open Failed", "Could not open file or folder.\n" + downloadedFile.getAbsolutePath());
            }
        });
    }

    private void handleOpenBrowser() {
        if (!browserService.openServer(server)) {
            String url = com.autodeploy.config.ApplicationConfig.getInstance().getFullBrowserUrl(server.getHost());
            CustomAlert.showError("Browser Error", "Failed to open browser.\nPlease open manually:\n" + url);
        }
    }

    private void toggleLogVisibility() {
        logVisible = !logVisible;
        logSection.setVisible(logVisible);
        logSection.setManaged(logVisible);
        toggleLogBtn.setText(logVisible ? "📋 Hide Logs" : "📋 Show Logs");
        if (logVisible) log("✓ Log panel opened");
    }

    // =================================================================================================================
    // HELPERS & UTILITIES
    // =================================================================================================================

    private void log(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            monitorLogForErrors(message);
        });
    }

    private void monitorLogForErrors(String message) {
        if (connectionManager != null && connectionManager.isConnected()) {
            String lowerMsg = message.toLowerCase();
            if (lowerMsg.contains("session is down") ||
                    lowerMsg.contains("ssh session not connected") ||
                    lowerMsg.contains("connection lost")) {
                connectionManager.notifyConnectionLost();
            }
        }
    }

    private boolean checkConnection() {
        if (!connectionManager.isConnected()) {
            CustomAlert.showError("Connection Error", MSG_NOT_CONNECTED);
            return false;
        }
        return true;
    }

    private void disableActionButtons(boolean disable) {
        Platform.runLater(() -> {
            restartServerBtn.setDisable(disable);
            downloadLogsBtn.setDisable(disable);
            buildProjectBtn.setDisable(disable);
            openBrowserBtn.setDisable(disable);
            disableUploadButtons(disable);
        });
    }

    private void disableUploadButtons(boolean disable) {
        Platform.runLater(() -> {
            uploadJarsBtn.setDisable(disable);
            uploadJspsBtn.setDisable(disable);
            uploadAllBtn.setDisable(disable);
        });
    }

    private void handleMaxStateChangeShape(WindowState state) {
        maxShape.setContent(Objects.equals(state, WindowState.MAXIMIZED) ? REST_SHAPE : MAX_SHAPE);
    }

    @Override
    protected double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }

    public void setSelectionWindow(SelectionWindow selectionWindow) {
        this.selectionWindow = selectionWindow;
    }
}