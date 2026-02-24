package com.autodeploy.service.deploy;

import com.autodeploy.domain.model.Project;
import com.autodeploy.infrastructure.connection.ConnectionManager;
import com.autodeploy.service.utility.FileSizeFormatter;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serviciu de upload fișiere (JAR și JSP) pe server prin SFTP.
 * <p>
 * Upload-ul e secvențial (fișier cu fișier) și se oprește automat
 * dacă detectează pierderea conexiunii mid-transfer.
 * <p>
 * Strategia de rezolvare a căilor diferă între JAR și JSP:
 * <ul>
 *   <li><b>JAR:</b> fișiere plate în directorul root (ex: "mylib-1.0.jar")</li>
 *   <li><b>JSP:</b> cale relativă cu subfoldere păstrate (ex: "pages/admin/index.jsp")</li>
 * </ul>
 * Diferența e abstractizată prin {@link PathResolver} (Strategy pattern).
 */
public class FileUploadService {

    private static final Logger LOGGER = Logger.getLogger(FileUploadService.class.getName());

    private static final String DEFAULT_CHECKBOX_STYLE =
            "-fx-font-size: 13px; -fx-text-fill: -color-fg-default; -fx-padding: 5px 5px 5px %dpx;";

    private final Project project;
    private final ConnectionManager connectionManager;
    private final Consumer<String> logger;

    public FileUploadService(Project project, ConnectionManager connectionManager, Consumer<String> logger) {
        this.project = project;
        this.connectionManager = connectionManager;
        this.logger = logger;
    }

    public UploadResult uploadJars(Map<String, CheckBox> jarCheckBoxMap) {
        return uploadFiles(jarCheckBoxMap, "JARs", new JarPathResolver());
    }

    public UploadResult uploadJsps(Map<String, CheckBox> jspCheckBoxMap) {
        return uploadFiles(jspCheckBoxMap, "JSPs", new JspPathResolver());
    }

    /**
     * Strategie de rezolvare a căilor locale/remote și a indentării checkbox-urilor.
     * JAR-urile au cale simplă (un singur nivel), JSP-urile păstrează structura de foldere.
     */
    private interface PathResolver {
        String getLocalPath(String fileName);
        String getRemotePath(String fileName);

        /** Indent-ul checkbox-ului în UI — JSP-urile au indent proporțional cu adâncimea folderului. */
        int getCheckboxIndent(String fileName);
    }

    private class JarPathResolver implements PathResolver {
        @Override
        public String getLocalPath(String fileName) {
            return project.getLocalJarPath() + File.separator + fileName;
        }

        @Override
        public String getRemotePath(String fileName) {
            return project.getRemoteJarPath() + "/" + fileName;
        }

        @Override
        public int getCheckboxIndent(String fileName) {
            return 5;
        }
    }

    /**
     * JSP path resolver — păstrează structura relativă de foldere.
     * Indent-ul în UI reflectă adâncimea: "pages/admin/index.jsp" → indent 55px (15 + 2*20).
     */
    private class JspPathResolver implements PathResolver {
        @Override
        public String getLocalPath(String relativePath) {
            return project.getLocalJspPath() + File.separator +
                    relativePath.replace("/", File.separator);
        }

        @Override
        public String getRemotePath(String relativePath) {
            return project.getRemoteJspPath() + "/" + relativePath;
        }

        @Override
        public int getCheckboxIndent(String relativePath) {
            int depth = relativePath.split("/").length - 1;
            return 15 + (depth * 20);
        }
    }

    /**
     * Upload generic pentru orice tip de fișiere.
     * <p>
     * Comportament la eroare de conexiune: se oprește imediat și marchează
     * toate fișierele rămase ca eșuate (nu încearcă upload pe conexiune moartă).
     * Checkbox-urile fișierelor uploadate cu succes sunt resetate (deselected + stil default).
     */
    private UploadResult uploadFiles(Map<String, CheckBox> checkBoxMap,
                                     String fileType,
                                     PathResolver pathResolver) {
        List<String> selectedFiles = getSelectedFiles(checkBoxMap);

        if (selectedFiles.isEmpty()) {
            return new UploadResult(0, 0, false);
        }

        log("📤 Starting upload of " + selectedFiles.size() + " " + fileType + " file(s)...");

        int successCount = 0;
        int failCount = 0;
        boolean connectionLost = false;

        for (String fileName : selectedFiles) {
            if (!connectionManager.isConnected()) {
                log("✗ Connection lost during upload!");
                connectionLost = true;
                failCount += (selectedFiles.size() - successCount - failCount);
                break;
            }

            try {
                String localPath = pathResolver.getLocalPath(fileName);
                String remotePath = pathResolver.getRemotePath(fileName);

                if (!uploadSingleFile(localPath, remotePath, fileName)) {
                    failCount++;
                    continue;
                }

                successCount++;
                resetCheckbox(checkBoxMap, fileName, pathResolver.getCheckboxIndent(fileName));

            } catch (Exception e) {
                log("  ✗ Failed to upload " + fileName + ": " + e.getMessage());
                failCount++;

                if (isConnectionError(e)) {
                    log("⚠ Connection error detected, stopping upload...");
                    connectionLost = true;
                    failCount += (selectedFiles.size() - successCount - failCount);
                    break;
                }
            }
        }

        logUploadSummary(fileType, successCount, failCount);
        return new UploadResult(successCount, failCount, connectionLost);
    }

    private boolean uploadSingleFile(String localPath, String remotePath, String displayName) {
        File localFile = new File(localPath);

        if (!localFile.exists()) {
            log("✗ File not found: " + displayName);
            return false;
        }

        try {
            log("  ↗ Uploading: " + displayName + " (" + FileSizeFormatter.format(localFile.length()) + ")");
            connectionManager.getSftpManager().uploadFile(localPath, remotePath);
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

    /**
     * Resetează checkbox-ul după upload reușit:
     * deselectează și aplică stilul default (elimină highlight-ul de "modified").
     */
    private void resetCheckbox(Map<String, CheckBox> checkBoxMap, String fileName, int indent) {
        Platform.runLater(() -> {
            CheckBox checkBox = checkBoxMap.get(fileName);
            if (checkBox != null) {
                checkBox.setSelected(false);
                checkBox.setStyle(String.format(DEFAULT_CHECKBOX_STYLE, indent));
            }
        });
    }

    /**
     * Detecție simplă a erorilor de conexiune bazată pe mesajul excepției.
     * Suficient pentru JSch care include "connection" sau "session" în mesaje.
     */
    private boolean isConnectionError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("connection") || msg.contains("session"));
    }

    private void logUploadSummary(String fileType, int successCount, int failCount) {
        log("--------------------------------");
        log("✓ " + fileType + ": " + successCount + " successful, " + failCount + " failed");
    }

    private void log(String message) {
        Platform.runLater(() -> logger.accept(message));
    }
}