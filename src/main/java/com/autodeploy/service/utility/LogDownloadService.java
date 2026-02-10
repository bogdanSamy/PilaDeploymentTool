package com.autodeploy.service.utility;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.infrastructure.sftp.SftpManager;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for downloading log files from remote server
 */
public class LogDownloadService {

    private static final Logger LOGGER = Logger.getLogger(LogDownloadService.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final SftpManager sftpManager;
    private final Consumer<String> logger;
    private final ApplicationConfig appConfig;

    public LogDownloadService(SftpManager sftpManager, Consumer<String> logger) {
        this.sftpManager = sftpManager;
        this.logger = logger;
        this.appConfig = ApplicationConfig.getInstance();
    }

    /**
     * Download result
     */
    public static class DownloadResult {
        private final boolean success;
        private final File downloadedFile;
        private final String errorMessage;

        public DownloadResult(boolean success, File downloadedFile, String errorMessage) {
            this.success = success;
            this.downloadedFile = downloadedFile;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public File getDownloadedFile() { return downloadedFile; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Validate download configuration
     */
    public DownloadResult validateConfiguration() {
        String remoteLogPath = appConfig.getRemoteLogPath();
        if (remoteLogPath == null || remoteLogPath.isEmpty()) {
            return new DownloadResult(false, null,
                    "Remote log path is not configured in app-config.properties");
        }

        String localDownloadDir = appConfig.getLocalDownloadDir();
        if (localDownloadDir == null || localDownloadDir.isEmpty()) {
            return new DownloadResult(false, null,
                    "Local download directory is not configured in app-config.properties");
        }

        // Create local download directory if needed
        File localDir = new File(localDownloadDir);
        if (!localDir.exists()) {
            if (!localDir.mkdirs()) {
                return new DownloadResult(false, null,
                        "Failed to create local download directory: " + localDownloadDir);
            }
            log("✓ Created local download directory: " + localDownloadDir);
        }

        return new DownloadResult(true, null, null);
    }

    /**
     * Download log file asynchronously
     */
    public Task<DownloadResult> downloadAsync() {
        return new Task<>() {
            @Override
            protected DownloadResult call() {
                return downloadLogFile();
            }
        };
    }

    /**
     * Download log file synchronously
     */
    public DownloadResult downloadLogFile() {
        log("📥 Starting log download...");

        // Validate configuration
        DownloadResult validation = validateConfiguration();
        if (!validation.isSuccess()) {
            log("✗ " + validation.getErrorMessage());
            return validation;
        }

        String remoteLogPath = appConfig.getRemoteLogPath();
        String localDownloadDir = appConfig.getLocalDownloadDir();

        log("✓ Remote log path: " + remoteLogPath);
        log("✓ Local download directory: " + localDownloadDir);

        try {
            // Get log file name
            String logFileName = remoteLogPath.substring(remoteLogPath.lastIndexOf('/') + 1);

            // Build local file path with timestamp
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String localFileName = logFileName.replace(".", "_" + timestamp + ".");
            String localFilePath = localDownloadDir + File.separator + localFileName;

            log("─────────────────────────────────────");
            log("▶ Downloading: " + logFileName);
            log("▶ From: " + remoteLogPath);
            log("▶ To: " + localFilePath);
            log("─────────────────────────────────────");

            // Download file
            sftpManager.downloadFile(remoteLogPath, localFilePath);

            File downloadedFile = new File(localFilePath);
            String fileSize = formatFileSize(downloadedFile.length());

            log("─────────────────────────────────────");
            log("✓ Download completed successfully");
            log("✓ File size: " + fileSize);
            log("✓ Saved to: " + localFilePath);
            log("─────────────────────────────────────");

            return new DownloadResult(true, downloadedFile, null);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Download error", e);
            log("✗ Download error: " + e.getMessage());
            return new DownloadResult(false, null, e.getMessage());
        }
    }

    /**
     * Open file with system's "Open With" dialog
     */
    public boolean openWithDialog(File file) {
        if (file == null || !file.exists()) {
            log("✗ File does not exist: " + file);
            return false;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                // Windows
                ProcessBuilder pb = new ProcessBuilder(
                        "rundll32.exe",
                        "shell32.dll,OpenAs_RunDLL",
                        file.getAbsolutePath()
                );
                pb.start();
                log("✓ 'Open With' dialog opened for: " + file.getName());
                return true;

            } else if (os.contains("mac")) {
                // macOS
                ProcessBuilder pb = new ProcessBuilder("open", "-a", "Finder", file.getAbsolutePath());
                pb.start();
                return true;

            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux
                ProcessBuilder pb = new ProcessBuilder("xdg-open", file.getAbsolutePath());
                pb.start();
                return true;
            }

            return false;

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to open 'Open With' dialog", e);
            log("✗ Failed to open 'Open With' dialog: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open file's containing folder
     */
    public boolean openContainingFolder(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            Desktop.getDesktop().open(file.getParentFile());
            log("✓ Opened containing folder");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open containing folder", e);
            log("✗ Failed to open folder: " + e.getMessage());
            return false;
        }
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
     * Log message
     */
    private void log(String message) {
        Platform.runLater(() -> logger.accept(message));
    }
}