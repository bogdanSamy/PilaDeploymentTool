package com.autodeploy.services;

import com.autodeploy.model.Server;
import com.autodeploy.sftp.SftpManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    private static final int RECONNECT_DELAY_MS = 1000;
    private static final int CONNECT_MAX_RETRIES = 3;

    private final Server server;
    private final Consumer<String> logger;

    private SftpManager sftpManager;
    private boolean isConnected = false;

    // Callbacks
    private Runnable onConnectionEstablished;
    private Runnable onConnectionLost;
    private Runnable onReconnectStarted;
    private Consumer<String> onConnectionFailed;

    public ConnectionManager(Server server, Consumer<String> logger) {
        this.server = server;
        this.logger = logger;
        this.sftpManager = new SftpManager(server);
    }

    public Task<Void> connectAsync() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                int attempt = 0;
                Exception lastError = null;
                while (attempt < CONNECT_MAX_RETRIES) {
                    attempt++;
                    try {
                        connect();
                        return null;
                    } catch (Exception e) {
                        lastError = e;
                        LOGGER.log(Level.SEVERE, "Connection attempt " + attempt + " failed", e);
                        int backoffMs = (int) Math.pow(2, attempt - 1) * 2000;
                        final String msg = "✗ Connection attempt " + attempt + " failed: " + e.getMessage();
                        Platform.runLater(() -> log(msg));
                        Thread.sleep(backoffMs);
                    }
                }

                Exception finalError = lastError;
                final String errMsg = finalError.getMessage();
                Platform.runLater(() -> {
                    if (onConnectionFailed != null) onConnectionFailed.accept(errMsg);
                });
                throw finalError;
            }
        };
    }

    public void connect() throws Exception {
        log("🔌 Connecting to server: " + server.getHost() + ":" + server.getPort());
        sftpManager.setConnectionStatusListener(this::notifyConnectionLost); // CHANGED: Use public method

        try {
            sftpManager.connect();
            isConnected = true;

            Platform.runLater(() -> {
                log("✓ Successfully connected to server");
                log("✓ SFTP session established");
                if (onConnectionEstablished != null) {
                    onConnectionEstablished.run();
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Connection failed", e);
            Platform.runLater(() -> log("✗ Failed to connect: " + e.getMessage()));
            throw e;
        }
    }

    public void disconnect() {
        if (!isConnected) return;
        log("🔌 Disconnecting from server...");
        try {
            if (sftpManager != null) {
                sftpManager.disconnect();
            }
            isConnected = false;
            log("✓ Disconnected from server");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during disconnect", e);
            log("✗ Error during disconnect: " + e.getMessage());
        }
    }

    public Task<Void> reconnectAsync() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> {
                    log("🔄 Attempting to reconnect...");
                    if (onReconnectStarted != null) onReconnectStarted.run();
                });
                disconnect();
                Thread.sleep(RECONNECT_DELAY_MS);
                sftpManager = new SftpManager(server);
                connect();
                return null;
            }
        };
    }

    public void notifyConnectionLost() {
        Platform.runLater(() -> {
            if (!isConnected) return;

            LOGGER.warning("Connection loss detected/notified");
            log("⚠ Connection lost to server!");
            isConnected = false;

            if (onConnectionLost != null) {
                onConnectionLost.run();
            }
        });
    }

    public boolean isConnected() {
        return isConnected && sftpManager.isConnected();
    }

    public SftpManager getSftpManager() { return sftpManager; }
    public void setSftpManager(SftpManager sftpManager) { this.sftpManager = sftpManager; }
    public void setOnConnectionEstablished(Runnable callback) { this.onConnectionEstablished = callback; }
    public void setOnConnectionLost(Runnable callback) { this.onConnectionLost = callback; }
    public void setOnReconnectStarted(Runnable callback) { this.onReconnectStarted = callback; }
    public void setOnConnectionFailed(Consumer<String> callback) { this.onConnectionFailed = callback; }

    private void log(String message) {
        logger.accept(message);
    }
}