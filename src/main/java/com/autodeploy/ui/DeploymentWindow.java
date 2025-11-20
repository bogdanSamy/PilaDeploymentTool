/*
 * Copyright © 2024. XTREME SOFTWARE SOLUTIONS
 *
 * All rights reserved. Unauthorized use, reproduction, or distribution
 * of this software or any portion of it is strictly prohibited and may
 * result in severe civil and criminal penalties. This code is the sole
 * proprietary of XTREME SOFTWARE SOLUTIONS.
 *
 * Commercialization, redistribution, and use without explicit permission
 * from XTREME SOFTWARE SOLUTIONS, are expressly forbidden.
 */

package com.autodeploy.ui;

import com.autodeploy.assets.Assets;
import com.autodeploy.config.ApplicationConfig;
import com.autodeploy.model.Project;
import com.autodeploy.model.Server;
import com.autodeploy.sftp.SftpManager;
import com.autodeploy.ui.dialogs.CustomAlert;
import com.autodeploy.ui.dialogs.NotificationController;
import com.autodeploy.watcher.FileWatcher;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import xss.it.nfx.NfxStage;
import xss.it.nfx.WindowState;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main Deployment Window
 *
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class DeploymentWindow extends NfxStage implements Initializable {

    @FXML
    private Button closeBtn;
    @FXML
    private Button maxBtn;
    @FXML
    private SVGPath maxShape;
    @FXML
    private Button minBtn;
    @FXML
    private ImageView iconView;
    @FXML
    private Label title;

    // Header
    @FXML
    private Label projectNameLabel;
    @FXML
    private Label serverNameLabel;
    @FXML
    private MFXButton changeBtn;

    // JAR Section
    @FXML
    private TitledPane jarSection;
    @FXML
    private VBox jarListContainer;
    @FXML
    private Label jarCountLabel;

    // JSP Section
    @FXML
    private TitledPane jspSection;
    @FXML
    private VBox jspListContainer;
    @FXML
    private Label jspCountLabel;
    @FXML
    private TextField jspSearchField;

    // Action Buttons
    @FXML
    private MFXButton restartServerBtn;
    @FXML
    private MFXButton downloadLogsBtn;
    @FXML
    private MFXButton buildProjectBtn;
    @FXML
    private MFXButton openBrowserBtn;
    @FXML
    private MFXButton uploadJarsBtn;
    @FXML
    private MFXButton uploadJspsBtn;
    @FXML
    private MFXButton uploadAllBtn;

    // Log Area
    @FXML
    private TextArea logArea;

    private final Project project;
    private final Server server;
    private SelectionWindow selectionWindow;

    private final Map<String, CheckBox> jarCheckBoxMap = new LinkedHashMap<>();
    private final Map<String, CheckBox> jspCheckBoxMap = new LinkedHashMap<>();

    private FileWatcher jarWatcher;
    private FileWatcher jspWatcher;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private SftpManager sftpManager;
    private StackPane rootPane;
    private VBox contentPane;
    private StackPane blurOverlay;
    private boolean isConnected = false;

    private Timeline restartTimerTimeline;
    private long restartStartTime;
    private String originalRestartButtonText = "Restart Server";

    public DeploymentWindow(Project project, Server server) {
        super();
        this.project = project;
        this.server = server;
        this.sftpManager = new SftpManager(server);

        try {
            getIcons().add(new Image(Assets.load("/icon.png").toExternalForm()));
            Parent parent = Assets.load("/deployment-window.fxml", this);

            // Wrap content in StackPane for overlay
            contentPane = (VBox) parent;
            rootPane = new StackPane();
            rootPane.getChildren().add(contentPane);

            createInitialBlurOverlay();

            Scene scene = new Scene(rootPane);
            setScene(scene);
            setTitle("Deployment Manager - " + project.getName());

            // ===== CLEANUP on close - interceptează ORICE metodă de închidere =====
            setOnCloseRequest(event -> {
                handleWindowClose();
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Handle window close - cleanup resources
     */
    private void handleWindowClose() {
        System.out.println("\n--- CLOSING DEPLOYMENT WINDOW - CLEANUP ---");

        log("🔌 Closing deployment window...");

        // Stop file watchers
        stopWatchers();

        // Disconnect from server
        disconnectFromServer();

        log("✓ All resources cleaned up");
        log("✓ Window closed");

        System.out.println("✓ Cleanup completed successfully\n");
    }

    @Override
    protected double getTitleBarHeight() {
        return 40;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Title bar setup
        getIcons().addListener((ListChangeListener<? super Image>) observable -> {
            if (!getIcons().isEmpty()) {
                iconView.setImage(getIcons().get(0));
            }
        });

        titleProperty().addListener(observable -> title.setText(getTitle()));

        setCloseControl(closeBtn); // ← LASĂ asta aici
        setMaxControl(maxBtn);
        setMinControl(minBtn);

        handleMaxStateChangeShape(getWindowState());
        windowStateProperty().addListener((obs, o, state) -> handleMaxStateChangeShape(state));

        // Initialize UI
        setupHeader();
        setupJarSection();
        setupJspSection();
        setupButtons();
        setupLogArea();

        // Start sections collapsed
        jarSection.setExpanded(false);
        jspSection.setExpanded(false);

        // Disable action buttons initially (until connected)
        disableActionButtons(true);

        log("✓ Deployment window initialized");
        log("Project: " + project.getName());
        log("Server: " + server.getName() + " (" + server.getHost() + ")");

        // Start file watchers
        startFileWatchers();

        // Note: SFTP connection will be started from SelectionWindow after show()
    }

    /**
     * Setup header with project and server info
     */
    private void setupHeader() {
        projectNameLabel.setText(project.getName());
        serverNameLabel.setText(server.getName() + " (" + server.getHost() + ")");

        // Change button - return to Selection Window
        changeBtn.setOnAction(e -> {
            returnToSelectionWindow();
        });
    }

    /**
     * Start file watchers for JAR and JSP directories
     */
    private void startFileWatchers() {
        // Start JAR watcher (non-recursive)
        if (project.getLocalJarPath() != null && !project.getLocalJarPath().isEmpty()) {
            jarWatcher = new FileWatcher(project.getLocalJarPath(), ".jar", this::handleJarChange, false);
            jarWatcher.start();
            log("👁 Watching JAR directory: " + project.getLocalJarPath());
        }

        // Start JSP watcher (RECURSIVE)
        if (project.getLocalJspPath() != null && !project.getLocalJspPath().isEmpty()) {
            jspWatcher = new FileWatcher(project.getLocalJspPath(), ".jsp", this::handleJspChange, true);
            jspWatcher.start();
            log("👁 Watching JSP directory (recursive): " + project.getLocalJspPath());
        }
    }


    /**
     * Stop file watchers
     */
    private void stopWatchers() {
        if (jarWatcher != null) {
            jarWatcher.stop();
            jarWatcher = null;
        }
        if (jspWatcher != null) {
            jspWatcher.stop();
            jspWatcher = null;
        }
        log("✓ Stopped file watchers");
    }



    /**
     * Setup JAR section with file list
     */
    private void setupJarSection() {
        jarListContainer.getChildren().clear();
        jarCheckBoxMap.clear();

        // Scan for JAR files
        List<File> jarFiles = scanForFiles(project.getLocalJarPath(), ".jar");

        if (jarFiles.isEmpty()) {
            Label noFiles = new Label("No JAR files found");
            noFiles.setStyle("-fx-text-fill: -color-fg-muted; -fx-padding: 10px;");
            jarListContainer.getChildren().add(noFiles);
        } else {
            for (File jarFile : jarFiles) {
                addJarFile(jarFile.getName(), false);
            }
        }

        updateJarCount();
    }


    /**
     * Setup JSP section with file list and search (RECURSIVE)
     */
    private void setupJspSection() {
        jspListContainer.getChildren().clear();
        jspCheckBoxMap.clear();

        // Scan for JSP files RECURSIVELY
        List<FileWithPath> jspFiles = scanForFilesRecursive(project.getLocalJspPath(), ".jsp");

        if (jspFiles.isEmpty()) {
            Label noFiles = new Label("No JSP files found");
            noFiles.setStyle("-fx-text-fill: -color-fg-muted; -fx-padding: 10px;");
            jspListContainer.getChildren().add(noFiles);
        } else {
            for (FileWithPath fileWithPath : jspFiles) {
                addJspFile(fileWithPath.relativePath, false);
            }
        }

        // Search functionality
        jspSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterJspFiles(newVal));

        updateJspCount();
    }

    /**
     * Filter JSP files based on search
     */
    private void filterJspFiles(String searchText) {
        jspListContainer.getChildren().clear();

        if (searchText == null || searchText.trim().isEmpty()) {
            // Show all in original order
            jspListContainer.getChildren().addAll(jspCheckBoxMap.values());
        } else {
            // Filter
            String lowerSearch = searchText.toLowerCase();
            List<CheckBox> filtered = jspCheckBoxMap.entrySet().stream()
                    .filter(entry -> entry.getKey().toLowerCase().contains(lowerSearch))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            jspListContainer.getChildren().addAll(filtered);
        }

        updateJspCount();
    }

    /**
     * Create initial blur overlay (called in constructor before show())
     */
    private void createInitialBlurOverlay() {
        // Apply blur effect to content
        GaussianBlur blur = new GaussianBlur(10);
        contentPane.setEffect(blur);

        // Create overlay
        blurOverlay = new StackPane();
        blurOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4);");

        // Create loading indicator
        VBox loadingBox = new VBox(20);
        loadingBox.setAlignment(javafx.geometry.Pos.CENTER);
        loadingBox.setStyle("-fx-background-color: -color-bg-default; " +
                "-fx-background-radius: 12px; " +
                "-fx-padding: 40px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");
        loadingBox.setMaxWidth(350);
        loadingBox.setMaxHeight(200);

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(60, 60);

        Label connectingLabel = new Label("Connecting to Server...");
        connectingLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

        Label serverLabel = new Label(server.getName() + " (" + server.getHost() + ")");
        serverLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-muted;");

        loadingBox.getChildren().addAll(progressIndicator, connectingLabel, serverLabel);
        blurOverlay.getChildren().add(loadingBox);

        // Add overlay to root IMMEDIATELY
        rootPane.getChildren().add(blurOverlay);
    }

    /**
     * Create styled checkbox for file
     */
    private CheckBox createFileCheckBox(String fileName, boolean checked) {
        CheckBox checkBox = new CheckBox(fileName);
        checkBox.setSelected(checked);
        checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-fg-default;");
        return checkBox;
    }

    /**
     * Handle JAR file change - auto-select new and modified files
     */
    private void handleJarChange(FileWatcher.FileChangeEvent event) {
        String fileName = event.getRelativePath();

        switch (event.getType()) {
            case ADDED:
                log("➕ New JAR detected: " + fileName);
                addJarFile(fileName, true); // ← Auto-select new files
                break;

            case MODIFIED:
                log("✏️ JAR modified: " + fileName);
                removeJarFile(fileName);
                addJarFile(fileName, true); // ← Auto-select modified files
                break;

            case DELETED:
                log("➖ JAR deleted: " + fileName);
                removeJarFile(fileName);
                break;
        }
    }

    /**
     * Handle JSP file change - auto-select new and modified files
     */
    private void handleJspChange(FileWatcher.FileChangeEvent event) {
        String relativePath = event.getRelativePath();

        switch (event.getType()) {
            case ADDED:
                log("➕ New JSP detected: " + relativePath);
                addJspFile(relativePath, true); // ← Auto-select new files
                break;

            case MODIFIED:
                log("✏️ JSP modified: " + relativePath);
                removeJspFile(relativePath);
                addJspFile(relativePath, true); // ← Auto-select modified files
                break;

            case DELETED:
                log("➖ JSP deleted: " + relativePath);
                removeJspFile(relativePath);
                break;
        }
    }

    /**
     * Add JAR file to list
     */
    private void addJarFile(String fileName, boolean checked) {
        Platform.runLater(() -> {
            // Remove if already exists
            CheckBox existingCheckBox = jarCheckBoxMap.get(fileName);
            if (existingCheckBox != null) {
                jarListContainer.getChildren().remove(existingCheckBox);
            }

            CheckBox checkBox = createFileCheckBox(fileName, checked);
            jarCheckBoxMap.put(fileName, checkBox);

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateJarCount());

            // Add at top (index 0)
            jarListContainer.getChildren().add(0, checkBox);

            // Highlight if checked (new/modified)
            if (checked) {
                checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-accent-emphasis; -fx-font-weight: bold;");
            } else {
                checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-fg-default;");
            }

            updateJarCount();
        });
    }

    /**
     * Remove JAR file from list
     */
    private void removeJarFile(String fileName) {
        Platform.runLater(() -> {
            CheckBox checkBox = jarCheckBoxMap.remove(fileName);
            if (checkBox != null) {
                jarListContainer.getChildren().remove(checkBox);
                updateJarCount();
            }
        });
    }

    /**
     * Add JSP file to list with folder hierarchy
     */
    private void addJspFile(String relativePath, boolean checked) {
        Platform.runLater(() -> {
            // Remove if already exists
            CheckBox existingCheckBox = jspCheckBoxMap.get(relativePath);
            if (existingCheckBox != null) {
                jspListContainer.getChildren().remove(existingCheckBox);
            }

            // Create checkbox with relative path (shows folder structure)
            CheckBox checkBox = createFileCheckBox(relativePath, checked);
            jspCheckBoxMap.put(relativePath, checkBox);

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateJspCount());

            // Re-apply filter if search is active
            String searchText = jspSearchField.getText();
            if (searchText == null || searchText.trim().isEmpty() ||
                    relativePath.toLowerCase().contains(searchText.toLowerCase())) {

                // Add at top (index 0)
                jspListContainer.getChildren().add(0, checkBox);

                // Calculate indentation based on folder depth
                int depth = relativePath.split("/").length - 1;
                int indent = 15 + (depth * 20);

                // Highlight if checked (new/modified)
                if (checked) {
                    checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px 5px 5px " + indent + "px; -fx-text-fill: -color-accent-emphasis; -fx-font-weight: bold;");
                } else {
                    checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px 5px 5px " + indent + "px; -fx-text-fill: -color-fg-default;");
                }
            }

            updateJspCount();
        });
    }

    /**
     * Remove JSP file from list
     */
    private void removeJspFile(String relativePath) {
        Platform.runLater(() -> {
            CheckBox checkBox = jspCheckBoxMap.remove(relativePath);
            if (checkBox != null) {
                jspListContainer.getChildren().remove(checkBox);
                updateJspCount();
            }
        });
    }

    /**
     * Update JAR count label
     */
    private void updateJarCount() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateJarCount);
            return;
        }

        long selected = jarCheckBoxMap.values().stream().filter(CheckBox::isSelected).count();
        long total = jarCheckBoxMap.size();
        jarCountLabel.setText(selected + " / " + total + " selected");
    }

    /**
     * Update JSP count label
     */
    private void updateJspCount() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateJspCount);
            return;
        }

        long selected = jspCheckBoxMap.values().stream().filter(CheckBox::isSelected).count();
        long total = jspCheckBoxMap.size();
        jspCountLabel.setText(selected + " / " + total + " selected");
    }


    /**
     * Scan directory for files with extension
     */
    private List<File> scanForFiles(String directoryPath, String extension) {
        List<File> files = new ArrayList<>();

        if (directoryPath == null || directoryPath.isEmpty()) {
            return files;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log("⚠ Directory not found: " + directoryPath);
            return files;
        }

        File[] foundFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(extension));
        if (foundFiles != null) {
            files.addAll(Arrays.asList(foundFiles));
            files.sort(Comparator.comparing(File::getName));
        }

        return files;
    }

    /**
     * Scan directory recursively for files with extension
     */
    private List<FileWithPath> scanForFilesRecursive(String directoryPath, String extension) {
        List<FileWithPath> files = new ArrayList<>();

        if (directoryPath == null || directoryPath.isEmpty()) {
            return files;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log("⚠ Directory not found: " + directoryPath);
            return files;
        }

        scanDirectoryRecursive(directory, "", extension, files);
        files.sort(Comparator.comparing(f -> f.relativePath));

        return files;
    }

    /**
     * Helper method to scan directory recursively
     */
    private void scanDirectoryRecursive(File directory, String relativePath, String extension, List<FileWithPath> result) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String newRelativePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                scanDirectoryRecursive(file, newRelativePath, extension, result);
            } else if (file.getName().toLowerCase().endsWith(extension)) {
                String fullPath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                result.add(new FileWithPath(file, fullPath));
            }
        }
    }

    /**
     * Setup action buttons
     */
    private void setupButtons() {
        // Restart Server
        restartServerBtn.setOnAction(e -> restartServer());

        // Download Logs
        downloadLogsBtn.setOnAction(e -> downloadLogs());

        // Build Project
        buildProjectBtn.setOnAction(e -> buildProject());

        // Open in Browser
        openBrowserBtn.setOnAction(e -> openInBrowser());

        // Upload JARs
        uploadJarsBtn.setOnAction(e -> uploadJars());

        // Upload JSPs
        uploadJspsBtn.setOnAction(e -> uploadJsps());

        // Upload All
        uploadAllBtn.setOnAction(e -> uploadAll());
    }

    /**
     * Setup log area
     */
    private void setupLogArea() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
    }

    /**
     * Log message with timestamp
     */
    private void log(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            logArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    /**
     * Restart server with confirmation
     */
    private void restartServer() {
        log("🔄 Restart server requested...");

        // Show blur overlay
        showBlurOverlayForDialog();

        // Show confirmation dialog
        boolean confirmed = CustomAlert.showConfirmation(
                "Restart Server",
                "Are you sure you want to restart the server?\n\n" +
                        "Server: " + server.getName() + " (" + server.getHost() + ")\n\n"
        );

        // Hide blur overlay
        hideBlurOverlay();

        if (confirmed) {
            log("✓ User confirmed server restart");
            executeServerRestart();
        } else {
            log("⚠ Server restart cancelled by user");
        }
    }

    /**
     * Execute server restart command
     */
    private void executeServerRestart() {
        log("🔄 Starting server restart process...");

        // Disable restart button
        restartServerBtn.setDisable(true);

        // Start timer animation in button
        startRestartTimer();

        // TODO: Replace this with your actual restart command
        String restartCommand = "YOUR_RESTART_COMMAND_HERE";

        // Create restart task
        Task<Void> restartTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Platform.runLater(() -> {
                        log("─────────────────────────────────────");
                        log("▶ Executing restart command...");
                        log("▶ Command: " + restartCommand);
                        log("─────────────────────────────────────");
                    });

                    // TODO: Execute your restart command via SSH/SFTP
                    // Example:
                    // sftpManager.executeCommand(restartCommand);

                    // Simulate restart delay (remove this when implementing real command)
                    Thread.sleep(5000); // 5 seconds simulation

                    Platform.runLater(() -> {
                        log("─────────────────────────────────────");
                        log("✓ Server restart command executed successfully");
                        log("⏳ Server is restarting... You can continue working.");
                        log("─────────────────────────────────────");

                        // Stop timer and re-enable restart button
                        stopRestartTimer();

                        // NOTIFICARE: Restart reușit

                        NotificationController notification1 = new NotificationController();
                        notification1.showSimpleNotification("Info", "The server has been restarted successfully.");

                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        log("✗ Server restart failed: " + e.getMessage());
                        e.printStackTrace();

                        // Stop timer and re-enable restart button
                        stopRestartTimer();

                        CustomAlert.showError(
                                "Restart Failed",
                                "Failed to restart server:\n" + e.getMessage()
                        );

                    });
                }

                return null;
            }
        };

        // Handle task failure
        restartTask.setOnFailed(event -> {
            Throwable error = restartTask.getException();
            log("✗ Restart task failed: " + error.getMessage());
            error.printStackTrace();

            // Stop timer and re-enable restart button
            stopRestartTimer();

            CustomAlert.showError(
                    "Restart Failed",
                    "Server restart task failed:\n" + error.getMessage()
            );
        });

        // Run restart in background thread
        Thread restartThread = new Thread(restartTask, "Server-Restart");
        restartThread.setDaemon(true);
        restartThread.start();
    }

    /**
     * Start restart timer animation in button
     */
    private void startRestartTimer() {
        // Save original button text
        originalRestartButtonText = restartServerBtn.getText();

        // Record start time
        restartStartTime = System.currentTimeMillis();

        // Create timeline that updates every 100ms
        restartTimerTimeline = new Timeline(
                new KeyFrame(Duration.millis(100), event -> {
                    long elapsedMillis = System.currentTimeMillis() - restartStartTime;
                    long elapsedSeconds = elapsedMillis / 1000;
                    long minutes = elapsedSeconds / 60;
                    long seconds = elapsedSeconds % 60;

                    // Format: MM:SS
                    String timeText = String.format("🔄 Restarting %02d:%02d", minutes, seconds);
                    restartServerBtn.setText(timeText);
                })
        );

        restartTimerTimeline.setCycleCount(Timeline.INDEFINITE);
        restartTimerTimeline.play();

        log("⏱ Restart timer started");
    }

    /**
     * Stop restart timer animation and restore button
     */
    private void stopRestartTimer() {
        if (restartTimerTimeline != null) {
            restartTimerTimeline.stop();
            restartTimerTimeline = null;
        }

        // Calculate final elapsed time
        long elapsedMillis = System.currentTimeMillis() - restartStartTime;
        long elapsedSeconds = elapsedMillis / 1000;
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;

        log(String.format("⏱ Restart completed in %02d:%02d", minutes, seconds));

        // Restore original button text
        restartServerBtn.setText(originalRestartButtonText);
        restartServerBtn.setDisable(false);
    }

    /**
     * Show blur overlay for dialog (simple, without progress)
     */
    private void showBlurOverlayForDialog() {
        Platform.runLater(() -> {
            // Apply blur effect to content
            GaussianBlur blur = new GaussianBlur(10);
            contentPane.setEffect(blur);

            // Create simple overlay (just blur, no content)
            blurOverlay = new StackPane();
            blurOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4);");

            // Add overlay to root
            rootPane.getChildren().add(blurOverlay);
        });
    }

    /**
     * Show blur overlay with progress indicator and message
     */
    private void showBlurOverlayWithProgress(String title, String subtitle) {
        Platform.runLater(() -> {
            // Apply blur effect to content
            GaussianBlur blur = new GaussianBlur(10);
            contentPane.setEffect(blur);

            // Create overlay
            blurOverlay = new StackPane();
            blurOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");

            // Create progress box
            VBox progressBox = new VBox(20);
            progressBox.setAlignment(javafx.geometry.Pos.CENTER);
            progressBox.setStyle("-fx-background-color: -color-bg-default; " +
                    "-fx-background-radius: 12px; " +
                    "-fx-padding: 40px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");
            progressBox.setMaxWidth(400);

            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setPrefSize(60, 60);

            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-muted;");
            subtitleLabel.setWrapText(true);
            subtitleLabel.setMaxWidth(320);
            subtitleLabel.setAlignment(javafx.geometry.Pos.CENTER);

            progressBox.getChildren().addAll(progressIndicator, titleLabel, subtitleLabel);
            blurOverlay.getChildren().add(progressBox);

            // Add overlay to root
            rootPane.getChildren().add(blurOverlay);
        });
    }

    /**
     * Download logs from server
     */
    private void downloadLogs() {
        log("📥 Starting log download...");

        // Get paths from ApplicationConfig
        String remoteLogPath = ApplicationConfig.getInstance().getRemoteLogPath();
        String localDownloadDir = ApplicationConfig.getInstance().getLocalDownloadDir();

        // Validate remote log path
        if (remoteLogPath == null || remoteLogPath.isEmpty()) {
            log("✗ Remote log path is not configured");
            CustomAlert.showError("Configuration Missing",
                    "Remote log path is not configured.\n\n" +
                            "Please configure 'download.remote.log.path' in app-config.properties");
            return;
        }

        // Validate local download directory
        if (localDownloadDir == null || localDownloadDir.isEmpty()) {
            log("✗ Local download directory is not configured");
            CustomAlert.showError("Configuration Missing",
                    "Local download directory is not configured.\n\n" +
                            "Please configure 'download.local.dir' in app-config.properties");
            return;
        }

        // Create local download directory if it doesn't exist
        File localDir = new File(localDownloadDir);
        if (!localDir.exists()) {
            boolean created = localDir.mkdirs();
            if (!created) {
                log("✗ Failed to create local download directory: " + localDownloadDir);
                CustomAlert.showError("Directory Creation Failed",
                        "Failed to create local download directory:\n" + localDownloadDir);
                return;
            }
            log("✓ Created local download directory: " + localDownloadDir);
        }

        // Check if connected to server
        if (!isConnectedToServer()) {
            log("✗ Not connected to server");
            CustomAlert.showError("Connection Error", "Not connected to server. Please reconnect.");
            return;
        }

        log("✓ Remote log path: " + remoteLogPath);
        log("✓ Local download directory: " + localDownloadDir);

        // Disable download button during download
        downloadLogsBtn.setDisable(true);

        // Create download task
        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Get log file name from remote path
                    String logFileName = remoteLogPath.substring(remoteLogPath.lastIndexOf('/') + 1);

                    // Build local file path with timestamp
                    String timestamp = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    String localFileName = logFileName.replace(".", "_" + timestamp + ".");
                    String localFilePath = localDownloadDir + File.separator + localFileName;

                    Platform.runLater(() -> {
                        log("─────────────────────────────────────");
                        log("▶ Downloading: " + logFileName);
                        log("▶ From: " + remoteLogPath);
                        log("▶ To: " + localFilePath);
                        log("─────────────────────────────────────");
                    });

                    // Download file via SFTP
                    sftpManager.downloadFile(remoteLogPath, localFilePath);

                    // Get downloaded file size
                    File downloadedFile = new File(localFilePath);
                    final String fileSize = formatFileSize(downloadedFile.length());

                    Platform.runLater(() -> {
                        log("─────────────────────────────────────");
                        log("✓ Download completed successfully");
                        log("✓ File size: " + fileSize);
                        log("✓ Saved to: " + localFilePath);
                        log("─────────────────────────────────────");

                        // Re-enable download button
                        downloadLogsBtn.setDisable(false);

                        final String fileName = downloadedFile.getName();

                        NotificationController notification = new NotificationController();
                        notification.showNotificationWithAction(
                                "File downloaded: " + fileName,
                                "Open With...",
                                () -> {
                                    log("🖱 User clicked 'Open With' button");
                                    boolean success = openWithDialog(downloadedFile);

                                    if (!success) {
                                        log("⚠ Could not open 'Open With' dialog");

                                        // Fallback: deschide folderul care conține fișierul
                                        try {
                                            Desktop.getDesktop().open(downloadedFile.getParentFile());
                                            log("✓ Opened containing folder instead");
                                        } catch (Exception ex) {
                                            log("✗ Failed to open folder: " + ex.getMessage());

                                            Platform.runLater(() -> {
                                                CustomAlert.showError(
                                                        "Open Failed",
                                                        "Could not open file or folder.\n\nFile location:\n" + downloadedFile.getAbsolutePath()
                                                );
                                            });
                                        }
                                    }
                                });
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        log("✗ Download error: " + e.getMessage());
                        e.printStackTrace();

                        CustomAlert.showError("Download Failed",
                                "Failed to download log file:\n" + e.getMessage());

                        // Re-enable download button
                        downloadLogsBtn.setDisable(false);
                    });
                }

                return null;
            }
        };

        // Handle task failure
        downloadTask.setOnFailed(event -> {
            Throwable error = downloadTask.getException();
            log("✗ Download task failed: " + error.getMessage());
            error.printStackTrace();

            CustomAlert.showError("Download Failed",
                    "Download task failed:\n" + error.getMessage());

            // Re-enable download button
            downloadLogsBtn.setDisable(false);
        });

        // Run download in background thread
        Thread downloadThread = new Thread(downloadTask, "Log-Download");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    /**
     * Build project using Ant
     */
    private void buildProject() {
        log("🔨 Starting project build...");
        log("Project: " + project.getName());

        // Check if build file path is configured
        if (project.getBuildFilePath() == null || project.getBuildFilePath().isEmpty()) {
            log("✗ Build file path not configured for this project");
            CustomAlert.showError("Build Configuration Missing",
                    "Build file path is not configured for project: " + project.getName() +
                            "\n\nPlease configure it in projects.json");
            return;
        }

        // Check if build file exists
        File buildFile = new File(project.getBuildFilePath());
        if (!buildFile.exists()) {
            log("✗ Build file not found: " + project.getBuildFilePath());
            CustomAlert.showError("Build File Not Found",
                    "Build file does not exist:\n" + project.getBuildFilePath());
            return;
        }

        // Get Ant path from ApplicationConfig
        String antPath = ApplicationConfig.getInstance().getAntPath();

        // Check if Ant is configured
        if (antPath == null || antPath.isEmpty()) {
            log("✗ Ant path is not configured");
            CustomAlert.showError("Ant Not Configured",
                    "Ant executable path is not configured.\n\n" +
                            "Please configure 'ant.path' in app-config.properties");
            return;
        }

        // Check if Ant executable exists
        File antFile = new File(antPath);
        if (!antFile.exists()) {
            log("✗ Ant executable not found: " + antPath);
            CustomAlert.showError("Ant Not Found",
                    "Ant executable does not exist:\n" + antPath + "\n\n" +
                            "Please check app-config.properties");
            return;
        }

        log("✓ Build file: " + project.getBuildFilePath());
        log("✓ Ant path: " + antPath);

        // Disable build button during build
        buildProjectBtn.setDisable(true);

        // Create build task
        Task<Void> buildTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Build command: ant.bat -f buildFilePath
                    ProcessBuilder processBuilder = new ProcessBuilder(
                            antPath,
                            "-f",
                            project.getBuildFilePath()
                    );

                    // Set working directory to build file's parent directory
                    processBuilder.directory(buildFile.getParentFile());

                    // Redirect error stream to output stream
                    processBuilder.redirectErrorStream(true);

                    Platform.runLater(() -> {
                        log("─────────────────────────────────────");
                        log("▶ Executing: " + antPath + " -f " + buildFile.getName());
                        log("▶ Working directory: " + buildFile.getParent());
                        log("─────────────────────────────────────");
                    });

                    // Start process
                    Process process = processBuilder.start();

                    // Read output in real-time
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String outputLine = line;
                            Platform.runLater(() -> log(outputLine));
                        }
                    }

                    // Wait for process to complete
                    int exitCode = process.waitFor();

                    Platform.runLater(() -> {
                        log("─────────────────────────────────────");
                        if (exitCode == 0) {
                            log("✓ Build completed successfully (exit code: " + exitCode + ")");
                        } else {
                            log("✗ Build failed with exit code: " + exitCode);
                        }
                        log("─────────────────────────────────────");

                        // Re-enable build button
                        buildProjectBtn.setDisable(false);
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        log("✗ Build error: " + e.getMessage());
                        e.printStackTrace();

                        CustomAlert.showError("Build Error",
                                "An error occurred during build:\n" + e.getMessage());

                        // Re-enable build button
                        buildProjectBtn.setDisable(false);
                    });
                }

                return null;
            }
        };

        // Handle task failure
        buildTask.setOnFailed(event -> {
            Throwable error = buildTask.getException();
            log("✗ Build task failed: " + error.getMessage());
            error.printStackTrace();

            CustomAlert.showError("Build Failed",
                    "Build task failed:\n" + error.getMessage());

            // Re-enable build button
            buildProjectBtn.setDisable(false);
        });

        // Run build in background thread
        Thread buildThread = new Thread(buildTask, "Ant-Build");
        buildThread.setDaemon(true);
        buildThread.start();
    }

    /**
     * Open server in browser
     */
    private void openInBrowser() {
        log("🌐 Opening server in browser...");

        // Get browser URL suffix from ApplicationConfig
        String browserSuffix = ApplicationConfig.getInstance().getBrowserUrlSuffix();

        // Build full URL
        String url = ApplicationConfig.getInstance().getFullBrowserUrl(server.getHost());

        log("✓ URL: " + url);

        try {
            // Check if Desktop is supported
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
                log("✓ Browser opened successfully");

            } else {
                log("✗ Desktop browse is not supported on this platform");

                // Fallback: try command line
                openInBrowserFallback(url);
            }
        } catch (Exception e) {
            log("✗ Failed to open browser: " + e.getMessage());
            e.printStackTrace();

            CustomAlert.showError(
                    "Browser Error",
                    "Failed to open browser:\n" + e.getMessage() + "\n\nURL: " + url
            );
        }
    }

    /**
            * Fallback method to open browser using command line
 */
    private void openInBrowserFallback(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
                log("✓ Browser opened via Windows command");
            } else if (os.contains("mac")) {
                // macOS
                Runtime.getRuntime().exec("open " + url);
                log("✓ Browser opened via macOS command");
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux/Unix
                Runtime.getRuntime().exec("xdg-open " + url);
                log("✓ Browser opened via Linux command");
            } else {
                log("✗ Unsupported operating system: " + os);
                CustomAlert.showError(
                        "Unsupported OS",
                        "Cannot open browser on this operating system.\n\nPlease open manually:\n" + url
                );
            }
        } catch (IOException e) {
            log("✗ Fallback browser open failed: " + e.getMessage());
            e.printStackTrace();

            CustomAlert.showError(
                    "Browser Error",
                    "Failed to open browser.\n\nPlease open manually:\n" + url
            );
        }
    }

    /**
     * Upload selected JAR files to server
     */
    private void uploadJars() {
        // Get selected JAR files
        List<String> selectedJars = jarCheckBoxMap.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();

        if (selectedJars.isEmpty()) {
            log("⚠ No JAR files selected");
            CustomAlert.showWarning("No Files Selected", "Please select at least one JAR file to upload.");
            return;
        }

        // Check if connected to server
        if (!isConnectedToServer()) {
            log("✗ Not connected to server");
            CustomAlert.showError("Connection Error", "Not connected to server. Please reconnect.");
            return;
        }

        // Disable upload button during upload
        uploadJarsBtn.setDisable(true);
        uploadAllBtn.setDisable(true);

        log("📤 Starting upload of " + selectedJars.size() + " JAR file(s)...");

        // Create upload task
        Task<Void> uploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int successCount = 0;
                int failCount = 0;

                for (String jarFileName : selectedJars) {
                    // CHECK CONNECTION BEFORE EACH FILE
                    if (!sftpManager.isConnected()) {
                        Platform.runLater(() -> {
                            log("✗ Connection lost during upload!");
                            log("⚠ Stopping upload process...");
                        });

                        // Mark remaining files as failed
                        failCount += (selectedJars.size() - successCount - failCount);
                        break; // Exit loop
                    }

                    try {
                        // Build local file path
                        String localFilePath = project.getLocalJarPath() + File.separator + jarFileName;
                        File localFile = new File(localFilePath);

                        if (!localFile.exists()) {
                            Platform.runLater(() -> log("✗ File not found: " + jarFileName));
                            failCount++;
                            continue;
                        }

                        // Build remote file path
                        String remoteFilePath = project.getRemoteJarPath() + "/" + jarFileName;

                        Platform.runLater(() -> log("  ↗ Uploading: " + jarFileName + " (" + formatFileSize(localFile.length()) + ")"));

                        // Upload file via SFTP
                        sftpManager.uploadFile(localFilePath, remoteFilePath);

                        Platform.runLater(() -> {
                            log("  ✓ Uploaded: " + jarFileName);
                            // Uncheck the uploaded file
                            CheckBox checkBox = jarCheckBoxMap.get(jarFileName);
                            if (checkBox != null) {
                                checkBox.setSelected(false);
                                // Remove highlight
                                checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-fg-default;");
                            }
                        });

                        successCount++;

                    } catch (Exception e) {
                        final String errorMsg = e.getMessage();
                        Platform.runLater(() -> log("  ✗ Failed to upload " + jarFileName + ": " + errorMsg));
                        failCount++;

                        // If it's a connection error, stop the upload
                        if (errorMsg != null && (errorMsg.contains("connection") || errorMsg.contains("session"))) {
                            Platform.runLater(() -> log("⚠ Connection error detected, stopping upload..."));
                            failCount += (selectedJars.size() - successCount - failCount);
                            break;
                        }
                    }
                }

                // Final summary (only in log, no alert)
                final int finalSuccess = successCount;
                final int finalFail = failCount;

                Platform.runLater(() -> {
                    log("─────────────────────────────────────");
                    log("✓ Upload completed: " + finalSuccess + " successful, " + finalFail + " failed");

                    // Re-enable upload buttons
                    uploadJarsBtn.setDisable(false);
                    uploadAllBtn.setDisable(false);
                });

                return null;
            }
        };

        // Handle task failure
        uploadTask.setOnFailed(event -> {
            Throwable error = uploadTask.getException();
            log("✗ Upload task failed: " + error.getMessage());
            error.printStackTrace();

            CustomAlert.showError("Upload Failed", "An error occurred during upload:\n" + error.getMessage());

            // Re-enable upload buttons
            uploadJarsBtn.setDisable(false);
            uploadAllBtn.setDisable(false);
        });

        // Run upload in background thread
        Thread uploadThread = new Thread(uploadTask, "JAR-Upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * Format file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Upload selected JSP files to server
     */
    private void uploadJsps() {
        // Get selected JSP files
        List<String> selectedJsps = jspCheckBoxMap.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();

        if (selectedJsps.isEmpty()) {
            log("⚠ No JSP files selected");
            CustomAlert.showWarning("No Files Selected", "Please select at least one JSP file to upload.");
            return;
        }

        // Check if connected to server
        if (!isConnectedToServer()) {
            log("✗ Not connected to server");
            CustomAlert.showError("Connection Error", "Not connected to server. Please reconnect.");
            return;
        }

        // Disable upload button during upload
        uploadJspsBtn.setDisable(true);
        uploadAllBtn.setDisable(true);

        log("📤 Starting upload of " + selectedJsps.size() + " JSP file(s)...");

        // Create upload task
        Task<Void> uploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int successCount = 0;
                int failCount = 0;

                for (String jspRelativePath : selectedJsps) {
                    // CHECK CONNECTION BEFORE EACH FILE
                    if (!sftpManager.isConnected()) {
                        Platform.runLater(() -> {
                            log("✗ Connection lost during upload!");
                            log("⚠ Stopping upload process...");
                        });

                        // Mark remaining files as failed
                        failCount += (selectedJsps.size() - successCount - failCount);
                        break; // Exit loop
                    }

                    try {
                        // Build local file path (with relative path from localJspPath)
                        String localFilePath = project.getLocalJspPath() + File.separator +
                                jspRelativePath.replace("/", File.separator);
                        File localFile = new File(localFilePath);

                        if (!localFile.exists()) {
                            Platform.runLater(() -> log("✗ File not found: " + jspRelativePath));
                            failCount++;
                            continue;
                        }

                        // Build remote file path (preserve folder structure)
                        String remoteFilePath = project.getRemoteJspPath() + "/" + jspRelativePath;

                        Platform.runLater(() -> log("  ↗ Uploading: " + jspRelativePath + " (" + formatFileSize(localFile.length()) + ")"));

                        // Upload file via SFTP
                        sftpManager.uploadFile(localFilePath, remoteFilePath);

                        Platform.runLater(() -> {
                            log("  ✓ Uploaded: " + jspRelativePath);
                            // Uncheck the uploaded file
                            CheckBox checkBox = jspCheckBoxMap.get(jspRelativePath);
                            if (checkBox != null) {
                                checkBox.setSelected(false);
                                // Remove highlight - preserve indentation
                                int depth = jspRelativePath.split("/").length - 1;
                                int indent = 15 + (depth * 20);
                                checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px 5px 5px " + indent + "px; -fx-text-fill: -color-fg-default;");
                            }
                        });

                        successCount++;

                    } catch (Exception e) {
                        final String errorMsg = e.getMessage();
                        Platform.runLater(() -> log("  ✗ Failed to upload " + jspRelativePath + ": " + errorMsg));
                        failCount++;

                        // If it's a connection error, stop the upload
                        if (errorMsg != null && (errorMsg.contains("connection") || errorMsg.contains("session"))) {
                            Platform.runLater(() -> log("⚠ Connection error detected, stopping upload..."));
                            failCount += (selectedJsps.size() - successCount - failCount);
                            break;
                        }
                    }
                }

                // Final summary (only in log, no alert)
                final int finalSuccess = successCount;
                final int finalFail = failCount;

                Platform.runLater(() -> {
                    log("─────────────────────────────────────");
                    log("✓ Upload completed: " + finalSuccess + " successful, " + finalFail + " failed");

                    // Re-enable upload buttons
                    uploadJspsBtn.setDisable(false);
                    uploadAllBtn.setDisable(false);
                });

                return null;
            }
        };

        // Handle task failure
        uploadTask.setOnFailed(event -> {
            Throwable error = uploadTask.getException();
            log("✗ Upload task failed: " + error.getMessage());
            error.printStackTrace();

            CustomAlert.showError("Upload Failed", "An error occurred during upload:\n" + error.getMessage());

            // Re-enable upload buttons
            uploadJspsBtn.setDisable(false);
            uploadAllBtn.setDisable(false);
        });

        // Run upload in background thread
        Thread uploadThread = new Thread(uploadTask, "JSP-Upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * Upload all selected files (JARs first, then JSPs)
     */
    private void uploadAll() {
        log("📤 Starting upload of all selected files...");

        // Get counts
        long jarCount = jarCheckBoxMap.values().stream().filter(CheckBox::isSelected).count();
        long jspCount = jspCheckBoxMap.values().stream().filter(CheckBox::isSelected).count();

        if (jarCount == 0 && jspCount == 0) {
            log("⚠ No files selected");
            CustomAlert.showWarning("No Files Selected", "Please select at least one file to upload.");
            return;
        }

        // Check if connected to server
        if (!isConnectedToServer()) {
            log("✗ Not connected to server");
            CustomAlert.showError("Connection Error", "Not connected to server. Please reconnect.");
            return;
        }

        log("📦 JARs to upload: " + jarCount);
        log("📄 JSPs to upload: " + jspCount);

        // Disable all upload buttons
        uploadJarsBtn.setDisable(true);
        uploadJspsBtn.setDisable(true);
        uploadAllBtn.setDisable(true);

        // Create sequential upload task
        Task<Void> uploadAllTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Upload JARs first
                if (jarCount > 0) {
                    Platform.runLater(() -> log("─── Uploading JARs ───"));
                    uploadJarsSequential();

                    // Check if connection still alive
                    if (!sftpManager.isConnected()) {
                        Platform.runLater(() -> {
                            log("✗ Connection lost during JAR upload");
                            log("⚠ Skipping JSP upload");
                        });
                        return null;
                    }
                }

                // Then upload JSPs
                if (jspCount > 0) {
                    Platform.runLater(() -> log("─── Uploading JSPs ───"));
                    uploadJspsSequential();
                }

                Platform.runLater(() -> {
                    log("═════════════════════════════════════");
                    log("✓ All uploads completed");

                    // Re-enable buttons
                    uploadJarsBtn.setDisable(false);
                    uploadJspsBtn.setDisable(false);
                    uploadAllBtn.setDisable(false);
                });

                return null;
            }
        };

        // Handle task failure
        uploadAllTask.setOnFailed(event -> {
            Throwable error = uploadAllTask.getException();
            log("✗ Upload all task failed: " + error.getMessage());
            error.printStackTrace();

            CustomAlert.showError("Upload Failed", "An error occurred during upload:\n" + error.getMessage());

            // Re-enable buttons
            uploadJarsBtn.setDisable(false);
            uploadJspsBtn.setDisable(false);
            uploadAllBtn.setDisable(false);
        });

        // Run in background
        Thread uploadThread = new Thread(uploadAllTask, "Upload-All");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * Upload JARs sequentially (synchronous, for use in uploadAll)
     */
    private void uploadJarsSequential() {
        List<String> selectedJars = jarCheckBoxMap.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();

        if (selectedJars.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (String jarFileName : selectedJars) {
            // CHECK CONNECTION BEFORE EACH FILE
            if (!sftpManager.isConnected()) {
                Platform.runLater(() -> {
                    log("✗ Connection lost during upload!");
                    log("⚠ Stopping upload process...");
                });
                failCount += (selectedJars.size() - successCount - failCount);
                break;
            }

            try {
                String localFilePath = project.getLocalJarPath() + File.separator + jarFileName;
                File localFile = new File(localFilePath);

                if (!localFile.exists()) {
                    Platform.runLater(() -> log("✗ File not found: " + jarFileName));
                    failCount++;
                    continue;
                }

                String remoteFilePath = project.getRemoteJarPath() + "/" + jarFileName;

                Platform.runLater(() -> log("  ↗ Uploading: " + jarFileName + " (" + formatFileSize(localFile.length()) + ")"));

                sftpManager.uploadFile(localFilePath, remoteFilePath);

                Platform.runLater(() -> {
                    log("  ✓ Uploaded: " + jarFileName);
                    CheckBox checkBox = jarCheckBoxMap.get(jarFileName);
                    if (checkBox != null) {
                        checkBox.setSelected(false);
                        checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-fg-default;");
                    }
                });

                successCount++;

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                Platform.runLater(() -> log("  ✗ Failed to upload " + jarFileName + ": " + errorMsg));
                failCount++;

                if (errorMsg != null && (errorMsg.contains("connection") || errorMsg.contains("session"))) {
                    Platform.runLater(() -> log("⚠ Connection error detected, stopping upload..."));
                    failCount += (selectedJars.size() - successCount - failCount);
                    break;
                }
            }
        }

        final int finalSuccess = successCount;
        final int finalFail = failCount;

        Platform.runLater(() -> {
            log("─────────────────────────────────────");
            log("✓ JARs: " + finalSuccess + " successful, " + finalFail + " failed");
        });
    }

    /**
     * Upload JSPs sequentially (synchronous, for use in uploadAll)
     */
    private void uploadJspsSequential() {
        List<String> selectedJsps = jspCheckBoxMap.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();

        if (selectedJsps.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (String jspRelativePath : selectedJsps) {
            // CHECK CONNECTION BEFORE EACH FILE
            if (!sftpManager.isConnected()) {
                Platform.runLater(() -> {
                    log("✗ Connection lost during upload!");
                    log("⚠ Stopping upload process...");
                });
                failCount += (selectedJsps.size() - successCount - failCount);
                break;
            }

            try {
                String localFilePath = project.getLocalJspPath() + File.separator +
                        jspRelativePath.replace("/", File.separator);
                File localFile = new File(localFilePath);

                if (!localFile.exists()) {
                    Platform.runLater(() -> log("✗ File not found: " + jspRelativePath));
                    failCount++;
                    continue;
                }

                String remoteFilePath = project.getRemoteJspPath() + "/" + jspRelativePath;

                Platform.runLater(() -> log("  ↗ Uploading: " + jspRelativePath + " (" + formatFileSize(localFile.length()) + ")"));

                sftpManager.uploadFile(localFilePath, remoteFilePath);

                Platform.runLater(() -> {
                    log("  ✓ Uploaded: " + jspRelativePath);
                    CheckBox checkBox = jspCheckBoxMap.get(jspRelativePath);
                    if (checkBox != null) {
                        checkBox.setSelected(false);
                        int depth = jspRelativePath.split("/").length - 1;
                        int indent = 15 + (depth * 20);
                        checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px 5px 5px " + indent + "px; -fx-text-fill: -color-fg-default;");
                    }
                });

                successCount++;

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                Platform.runLater(() -> log("  ✗ Failed to upload " + jspRelativePath + ": " + errorMsg));
                failCount++;

                if (errorMsg != null && (errorMsg.contains("connection") || errorMsg.contains("session"))) {
                    Platform.runLater(() -> log("⚠ Connection error detected, stopping upload..."));
                    failCount += (selectedJsps.size() - successCount - failCount);
                    break;
                }
            }
        }

        final int finalSuccess = successCount;
        final int finalFail = failCount;

        Platform.runLater(() -> {
            log("─────────────────────────────────────");
            log("✓ JSPs: " + finalSuccess + " successful, " + finalFail + " failed");
        });
    }

    private void handleMaxStateChangeShape(WindowState state) {
        if (Objects.equals(state, WindowState.MAXIMIZED)) {
            maxShape.setContent(REST_SHAPE);
        } else {
            maxShape.setContent(MAX_SHAPE);
        }
    }

    /**
     * Connect to server via SFTP with loading overlay
     */
    public void connectToServer() {

        // Disable all action buttons during connection
        disableActionButtons(true);

        log("🔌 Connecting to server: " + server.getHost() + ":" + server.getPort());

        // Set connection status listener
        sftpManager.setConnectionStatusListener(this::onConnectionLost);

        // Create connection task
        Task<Void> connectionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Attempt SFTP connection
                    sftpManager.connect();
                    isConnected = true;

                    Platform.runLater(() -> {
                        log("✓ Successfully connected to server");
                        log("✓ SFTP session established");
                        hideBlurOverlay();
                        disableActionButtons(false);
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        log("✗ Failed to connect: " + e.getMessage());
                        hideBlurOverlay();

                        CustomAlert.showError(
                                "Connection Failed",
                                "Could not connect to server:\n" +
                                        server.getHost() + ":" + server.getPort() + "\n\n" +
                                        "Error: " + e.getMessage()
                        );

                        // Close window on connection failure
                        close();
                    });
                }
                return null;
            }
        };

        // Run connection in background thread
        Thread connectionThread = new Thread(connectionTask, "SFTP-Connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    /**
     * Called when connection is lost
     */
    private void onConnectionLost() {
        Platform.runLater(() -> {
            log("⚠ Connection lost to server!");
            isConnected = false;

            // Show blur overlay with reconnect option
            showReconnectOverlay();

            // Disable action buttons
            disableActionButtons(true);
        });
    }

    /**
     * Show reconnect overlay
     */
    private void showReconnectOverlay() {
        Platform.runLater(() -> {
            // Apply blur effect to content
            GaussianBlur blur = new GaussianBlur(10);
            contentPane.setEffect(blur);

            // Create overlay
            blurOverlay = new StackPane();
            blurOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");

            // Create reconnect box
            VBox reconnectBox = new VBox(20);
            reconnectBox.setAlignment(javafx.geometry.Pos.CENTER);
            reconnectBox.setStyle("-fx-background-color: -color-bg-default; " +
                    "-fx-background-radius: 12px; " +
                    "-fx-padding: 40px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");
            reconnectBox.setMaxWidth(400);

            // Warning icon (SVG)
            Label warningIcon = new Label("⚠️");
            warningIcon.setStyle("-fx-font-size: 48px;");

            Label titleLabel = new Label("Connection Lost");
            titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #cf222e;");

            Label messageLabel = new Label("The connection to the server was lost.");
            messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-default;");
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(320);
            messageLabel.setAlignment(javafx.geometry.Pos.CENTER);

            Label serverLabel = new Label(server.getName() + " (" + server.getHost() + ")");
            serverLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-fg-muted;");

            // Buttons
            HBox buttonBox = new HBox(15);
            buttonBox.setAlignment(javafx.geometry.Pos.CENTER);

            MFXButton reconnectBtn = new MFXButton("Reconnect");
            reconnectBtn.setStyle("-fx-background-color: -color-accent-emphasis; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-cursor: hand; " +
                    "-fx-background-radius: 6px;");
            reconnectBtn.setPrefWidth(120);
            reconnectBtn.setPrefHeight(40);
            reconnectBtn.setOnAction(e -> {
                reconnectToServer();
            });

            MFXButton closeBtn = new MFXButton("Close");
            closeBtn.setStyle("-fx-background-color: -color-bg-subtle; " +
                    "-fx-text-fill: -color-fg-default; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-cursor: hand; " +
                    "-fx-background-radius: 6px; " +
                    "-fx-border-color: -color-border-default; " +
                    "-fx-border-width: 1.5px;");
            closeBtn.setPrefWidth(120);
            closeBtn.setPrefHeight(40);
            closeBtn.setOnAction(e -> close());

            buttonBox.getChildren().addAll(reconnectBtn, closeBtn);

            reconnectBox.getChildren().addAll(
                    warningIcon,
                    titleLabel,
                    messageLabel,
                    serverLabel,
                    buttonBox
            );

            blurOverlay.getChildren().add(reconnectBox);

            // Add overlay to root
            rootPane.getChildren().add(blurOverlay);
        });
    }

    /**
     * Reconnect to server
     */
    private void reconnectToServer() {
        log("🔄 Attempting to reconnect...");

        // First, hide the "Connection Lost" overlay
        hideBlurOverlay();

        // Wait a tiny bit for UI to update, then show connecting overlay
        Platform.runLater(() -> {
            // Show connecting blur overlay
            showBlurOverlay("Reconnecting to Server...", server.getName() + " (" + server.getHost() + ")");

            // Disconnect first
            disconnectFromServer();

            // Wait a bit before reconnecting
            Task<Void> reconnectTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    Thread.sleep(1000); // Wait 1 second

                    Platform.runLater(() -> {
                        // Create new SFTP manager
                        sftpManager = new SftpManager(server);

                        // Connect again (will hide blur when done)
                        connectToServer();
                    });

                    return null;
                }
            };

            Thread reconnectThread = new Thread(reconnectTask, "SFTP-Reconnect");
            reconnectThread.setDaemon(true);
            reconnectThread.start();
        });
    }

    /**
     * Disconnect from server
     */
    private void disconnectFromServer() {
        if (sftpManager != null && sftpManager.isConnected()) {
            sftpManager.disconnect();
            isConnected = false;
            log("✓ Disconnected from server: " + server.getHost());
        }
    }

    /**
     * Show blur overlay with loading indicator
     */
    private void showBlurOverlay(String title, String subtitle) {
        Platform.runLater(() -> {
            // Apply blur effect to content
            GaussianBlur blur = new GaussianBlur(10);
            contentPane.setEffect(blur);

            // Create overlay
            blurOverlay = new StackPane();
            blurOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4);");

            // Create loading indicator
            VBox loadingBox = new VBox(20);
            loadingBox.setAlignment(javafx.geometry.Pos.CENTER);
            loadingBox.setStyle("-fx-background-color: -color-bg-default; " +
                    "-fx-background-radius: 12px; " +
                    "-fx-padding: 40px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);");
            loadingBox.setMaxWidth(350);
            loadingBox.setMaxHeight(200);

            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setPrefSize(60, 60);

            Label connectingLabel = new Label(title);
            connectingLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

            Label serverLabel = new Label(subtitle);
            serverLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-muted;");

            loadingBox.getChildren().addAll(progressIndicator, connectingLabel, serverLabel);
            blurOverlay.getChildren().add(loadingBox);

            // Add overlay to root
            rootPane.getChildren().add(blurOverlay);
        });
    }

    /**
     * Shortcut for initial connection
     */
    public void showBlurOverlay() {
        showBlurOverlay("Connecting to Server...", server.getName() + " (" + server.getHost() + ")");
    }

    /**
     * Return to Selection Window (cleanup and close) - WITH CustomAlert
     */
    private void returnToSelectionWindow() {
        log("🔄 Requesting return to selection window...");

        // Show confirmation using CustomAlert
        boolean confirmed = CustomAlert.showConfirmation(
                "Change Project/Server",
                "This will disconnect from the server and return to the selection window.\n\nAre you sure?"
        );

        if (confirmed) {
            // Perform cleanup
            log("✓ User confirmed return to selection");
            log("✓ Performing cleanup...");

            // Stop file watchers
            stopWatchers();

            // Disconnect from server
            disconnectFromServer();

            log("✓ Cleanup completed");

            // Reopen Selection Window
            if (selectionWindow != null) {
                Platform.runLater(() -> {
                    selectionWindow.show();
                });
            } else {
                // Create new selection window if reference is lost
                Platform.runLater(() -> {
                    try {
                        SelectionWindow newSelectionWindow = new SelectionWindow();
                        newSelectionWindow.show();
                    } catch (Exception e) {
                        System.err.println("✗ Error creating selection window: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }

            // Close Deployment Window
            this.close();
        } else {
            log("⚠ Return to selection cancelled by user");
        }
    }

    /**
     * Hide blur overlay
     */
    private void hideBlurOverlay() {
        Platform.runLater(() -> {
            // Remove blur effect
            contentPane.setEffect(null);

            // Remove overlay
            if (blurOverlay != null) {
                rootPane.getChildren().remove(blurOverlay);
                blurOverlay = null;
            }
        });
    }

    /**
     * Disable/enable action buttons
     */
    private void disableActionButtons(boolean disable) {
        Platform.runLater(() -> {
            restartServerBtn.setDisable(disable);
            downloadLogsBtn.setDisable(disable);
            buildProjectBtn.setDisable(disable);
            openBrowserBtn.setDisable(disable);
            uploadJarsBtn.setDisable(disable);
            uploadJspsBtn.setDisable(disable);
            uploadAllBtn.setDisable(disable);
        });
    }

    /**
     * Deschide dialogul "Open with" din Windows pentru un fișier
     *
     * @param file Fișierul pentru care se deschide dialogul
     * @return true dacă operațiunea a reușit, false altfel
     */
    private boolean openWithDialog(File file) {
        if (file == null || !file.exists()) {
            log("✗ Fișierul nu există sau este null: " + file);
            return false;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows: folosește rundll32.exe cu shell32.dll,OpenAs_RunDLL
                ProcessBuilder pb = new ProcessBuilder(
                        "rundll32.exe",
                        "shell32.dll,OpenAs_RunDLL",
                        file.getAbsolutePath()
                );
                pb.start();
                log("✓ Dialog 'Open with' deschis pentru: " + file.getName());
                return true;

            } else if (os.contains("mac")) {
                // macOS: deschide cu Finder pentru a selecta aplicația
                ProcessBuilder pb = new ProcessBuilder(
                        "open",
                        "-a",
                        "Finder",
                        file.getAbsolutePath()
                );
                pb.start();
                return true;

            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux: folosește xdg-open
                ProcessBuilder pb = new ProcessBuilder(
                        "xdg-open",
                        file.getAbsolutePath()
                );
                pb.start();
                return true;
            }

            return false;

        } catch (IOException e) {
            log("✗ Eroare la deschiderea dialogului 'Open with': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get SFTP manager
     */
    public SftpManager getSftpManager() {
        return sftpManager;
    }
    // Add setter method
    public void setSelectionWindow(SelectionWindow selectionWindow) {
        this.selectionWindow = selectionWindow;
    }
    /**
     * Check if connected to server
     */
    public boolean isConnectedToServer() {
        return isConnected && sftpManager.isConnected();
    }

    public static final String MIN_SHAPE = "M1 7L1 8L14 8L14 7Z";
    public static final String MAX_SHAPE = "M2.5 2 A 0.50005 0.50005 0 0 0 2 2.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L13.5 14 A 0.50005 0.50005 0 0 0 14 13.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L2.5 2 z M 3 3L13 3L13 13L3 13L3 3 z";
    public static final String REST_SHAPE = "M4.5 2 A 0.50005 0.50005 0 0 0 4 2.5L4 4L2.5 4 A 0.50005 0.50005 0 0 0 2 4.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L11.5 14 A 0.50005 0.50005 0 0 0 12 13.5L12 12L13.5 12 A 0.50005 0.50005 0 0 0 14 11.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L4.5 2 z M 5 3L13 3L13 11L12 11L12 4.5 A 0.50005 0.50005 0 0 0 11.5 4L5 4L5 3 z M 3 5L11 5L11 13L3 13L3 5 z";

    /**
     * Helper class to hold file with relative path
     */
    private static class FileWithPath {
        final File file;
        final String relativePath;

        FileWithPath(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }
    }
}

