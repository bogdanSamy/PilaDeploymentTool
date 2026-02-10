package com.autodeploy.service.deploy;

import com.autodeploy.domain.model.Project;
import com.autodeploy.infrastructure.sftp.SftpManager;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUploadService {

    private static final Logger LOGGER = Logger.getLogger(FileUploadService.class.getName());

    private final Project project;
    private final SftpManager sftpManager;
    private final Consumer<String> logger;

    public FileUploadService(Project project, SftpManager sftpManager, Consumer<String> logger) {
        this.project = project;
        this.sftpManager = sftpManager;
        this.logger = logger;
    }

    public static class UploadResult {
        private final int successCount;
        private final int failCount;
        private final boolean connectionLost;

        public UploadResult(int successCount, int failCount, boolean connectionLost) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.connectionLost = connectionLost;
        }

        public int getSuccessCount() { return successCount; }
        public int getFailCount() { return failCount; }
        public boolean isConnectionLost() { return connectionLost; }
        public int getTotalCount() { return successCount + failCount; }
    }

    public UploadResult uploadJars(Map<String, CheckBox> jarCheckBoxMap) {
        List<String> selectedJars = getSelectedFiles(jarCheckBoxMap);

        if (selectedJars.isEmpty()) {
            return new UploadResult(0, 0, false);
        }

        log("📤 Starting upload of " + selectedJars.size() + " JAR file(s)...");

        int successCount = 0;
        int failCount = 0;
        boolean connectionLost = false;

        for (String jarFileName : selectedJars) {
            if (!sftpManager.isConnected()) {
                log("✗ Connection lost during upload!");
                connectionLost = true;
                failCount += (selectedJars.size() - successCount - failCount);
                break;
            }

            try {
                String localPath = project.getLocalJarPath() + File.separator + jarFileName;
                String remotePath = project.getRemoteJarPath() + "/" + jarFileName;

                if (!uploadFile(localPath, remotePath, jarFileName)) {
                    failCount++;
                    continue;
                }

                successCount++;
                uncheckAndUnhighlightFile(jarCheckBoxMap, jarFileName, 5);

            } catch (Exception e) {
                log("  ✗ Failed to upload " + jarFileName + ": " + e.getMessage());
                failCount++;

                if (isConnectionError(e)) {
                    log("⚠ Connection error detected, stopping upload...");
                    connectionLost = true;
                    failCount += (selectedJars.size() - successCount - failCount);
                    break;
                }
            }
        }

        logUploadSummary("JARs", successCount, failCount);
        return new UploadResult(successCount, failCount, connectionLost);
    }

    public UploadResult uploadJsps(Map<String, CheckBox> jspCheckBoxMap) {
        List<String> selectedJsps = getSelectedFiles(jspCheckBoxMap);

        if (selectedJsps.isEmpty()) {
            return new UploadResult(0, 0, false);
        }

        log("📤 Starting upload of " + selectedJsps.size() + " JSP file(s)...");

        int successCount = 0;
        int failCount = 0;
        boolean connectionLost = false;

        for (String jspRelativePath : selectedJsps) {
            if (!sftpManager.isConnected()) {
                log("✗ Connection lost during upload!");
                connectionLost = true;
                failCount += (selectedJsps.size() - successCount - failCount);
                break;
            }

            try {
                String localPath = project.getLocalJspPath() + File.separator +
                        jspRelativePath.replace("/", File.separator);
                String remotePath = project.getRemoteJspPath() + "/" + jspRelativePath;

                if (!uploadFile(localPath, remotePath, jspRelativePath)) {
                    failCount++;
                    continue;
                }

                successCount++;

                // Calculate indentation for JSP files
                int depth = jspRelativePath.split("/").length - 1;
                int indent = 15 + (depth * 20);
                uncheckAndUnhighlightFile(jspCheckBoxMap, jspRelativePath, indent);

            } catch (Exception e) {
                log("  ✗ Failed to upload " + jspRelativePath + ": " + e.getMessage());
                failCount++;

                if (isConnectionError(e)) {
                    log("⚠ Connection error detected, stopping upload...");
                    connectionLost = true;
                    failCount += (selectedJsps.size() - successCount - failCount);
                    break;
                }
            }
        }

        logUploadSummary("JSPs", successCount, failCount);
        return new UploadResult(successCount, failCount, connectionLost);
    }

    private boolean uploadFile(String localPath, String remotePath, String displayName) {
        File localFile = new File(localPath);

        if (!localFile.exists()) {
            log("✗ File not found: " + displayName);
            return false;
        }

        try {
            log("  ↗ Uploading: " + displayName + " (" + formatFileSize(localFile.length()) + ")");
            sftpManager.uploadFile(localPath, remotePath);
            log("  ✓ Uploaded: " + displayName);
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to upload " + displayName, e);
            throw new RuntimeException(e);
        }
    }

    private List<String> getSelectedFiles(Map<String, CheckBox> checkBoxMap) {
        return checkBoxMap.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
    }

    private void uncheckAndUnhighlightFile(Map<String, CheckBox> checkBoxMap, String fileName, int basePadding) {
        Platform.runLater(() -> {
            CheckBox checkBox = checkBoxMap.get(fileName);
            if (checkBox != null) {
                checkBox.setSelected(false);
                checkBox.setStyle("-fx-font-size: 13px; -fx-padding: 5px 5px 5px " + basePadding + "px; -fx-text-fill: -color-fg-default;");
            }
        });
    }

    private boolean isConnectionError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("connection") || msg.contains("session"));
    }

    private void logUploadSummary(String fileType, int successCount, int failCount) {
        log("--------------------------------");
        log("✓ " + fileType + ": " + successCount + " successful, " + failCount + " failed");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private void log(String message) {
        Platform.runLater(() -> logger.accept(message));
    }
}