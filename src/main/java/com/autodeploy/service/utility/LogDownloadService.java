package com.autodeploy.service.utility;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.domain.model.Server;
import com.autodeploy.infrastructure.connection.ConnectionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Descarcă fișiere de log de pe server prin SFTP.
 * <p>
 * Fișierul descărcat primește un suffix de timestamp în nume pentru a evita
 * suprascrierea descărcărilor anterioare (ex: "server.log" → "server_20260223_143012.log").
 * <p>
 * Fluxul: validare config → verificare conexiune → download SFTP → returnare {@link DownloadResult}.
 */
public class LogDownloadService {

    private static final Logger LOGGER = Logger.getLogger(LogDownloadService.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ConnectionManager connectionManager;
    private final Consumer<String> logger;
    private final ApplicationConfig appConfig;
    private final Server server;

    public LogDownloadService(Server server, ConnectionManager connectionManager, Consumer<String> logger) {
        this.server = server;
        this.connectionManager = connectionManager;
        this.logger = logger;
        this.appConfig = ApplicationConfig.getInstance();
    }

    /**
     * Validează configurația necesară pentru download:
     * calea remotă a log-ului și directorul local de descărcare.
     * Creează directorul local dacă nu există.
     */
    public DownloadResult validateConfiguration() {
        String remoteLogPath = server.getRemoteLogPath();
        if (remoteLogPath == null || remoteLogPath.isEmpty()) {
            return DownloadResult.failure(
                    "Remote log path is not configured in app-config.properties");
        }

        String localDownloadDir = appConfig.getLocalDownloadDir();
        if (localDownloadDir == null || localDownloadDir.isEmpty()) {
            return DownloadResult.failure(
                    "Local download directory is not configured in app-config.properties");
        }

        File localDir = new File(localDownloadDir);
        if (!localDir.exists() && !localDir.mkdirs()) {
            return DownloadResult.failure(
                    "Failed to create local download directory: " + localDownloadDir);
        }

        return DownloadResult.success(null);
    }

    public Task<DownloadResult> downloadAsync() {
        return new Task<>() {
            @Override
            protected DownloadResult call() {
                return downloadLogFile();
            }
        };
    }

    public DownloadResult downloadLogFile() {
        log("📥 Starting log download...");

        DownloadResult validation = validateConfiguration();
        if (!validation.isSuccess()) {
            log("✗ " + validation.getErrorMessage());
            return validation;
        }

        if (!connectionManager.isConnected()) {
            log("✗ Not connected to server");
            return DownloadResult.failure("Not connected to server. Please reconnect and try again.");
        }

        String remoteLogPath = server.getRemoteLogPath();
        String localDownloadDir = appConfig.getLocalDownloadDir();

        log("✓ Remote log path: " + remoteLogPath);
        log("✓ Local download directory: " + localDownloadDir);

        try {
            String localFilePath = buildLocalFilePath(remoteLogPath, localDownloadDir);

            log("=====================================");
            log("▶ Downloading: " + extractFileName(remoteLogPath));
            log("▶ From: " + remoteLogPath);
            log("▶ To: " + localFilePath);
            log("=====================================");

            connectionManager.getSftpManager().downloadFile(remoteLogPath, localFilePath);

            File downloadedFile = new File(localFilePath);

            log("=====================================");
            log("✓ Download completed successfully");
            log("✓ File size: " + FileSizeFormatter.format(downloadedFile.length()));
            log("✓ Saved to: " + localFilePath);
            log("=====================================");

            return DownloadResult.success(downloadedFile);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Download error", e);
            log("✗ Download error: " + e.getMessage());
            return DownloadResult.failure(e.getMessage());
        }
    }

    /**
     * Construiește calea locală cu timestamp unic.
     * "server.log" + "20260223_143012" → "server_20260223_143012.log"
     * Astfel fiecare descărcare e un fișier nou, fără pierdere de date.
     */
    private String buildLocalFilePath(String remoteLogPath, String localDownloadDir) {
        String logFileName = extractFileName(remoteLogPath);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String localFileName = logFileName.replace(".", "_" + timestamp + ".");
        return localDownloadDir + File.separator + localFileName;
    }

    /** Extrage numele fișierului din calea remotă (ultimul segment după '/'). */
    private String extractFileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private void log(String message) {
        Platform.runLater(() -> logger.accept(message));
    }
}