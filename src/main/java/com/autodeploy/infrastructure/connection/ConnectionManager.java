package com.autodeploy.infrastructure.connection;

import com.autodeploy.domain.model.Server;
import com.autodeploy.infrastructure.sftp.SftpManager;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionează ciclul de viață al conexiunii SSH/SFTP către un server.
 * <p>
 * Responsabilități:
 * <ul>
 *   <li>Conectare asincronă cu retry + exponential backoff</li>
 *   <li>Reconectare (distruge sesiunea veche, creează una nouă)</li>
 *   <li>Notificări către UI prin callbacks (connected/lost/failed)</li>
 * </ul>
 * <p>
 * <b>Atenție:</b> La reconectare se creează un {@link SftpManager} nou.
 * Serviciile consumatoare trebuie să apeleze {@link #getSftpManager()} la fiecare
 * operație, fără a stoca referința local.
 */
public class ConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    private static final int RECONNECT_DELAY_MS = 1_000;
    private static final int CONNECT_MAX_RETRIES = 3;

    private final Server server;
    private final Consumer<String> logger;

    /**
     * SftpManager curent. Se recrează la reconectare.
     * Serviciile TREBUIE să acceseze prin getSftpManager() la fiecare operație,
     * NU să stocheze referința local.
     */
    private volatile SftpManager sftpManager;
    private volatile boolean isConnected = false;

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
                connectWithRetries();
                return null;
            }
        };
    }

    /**
     * Reconectare asincronă: deconectează sesiunea curentă, așteaptă un delay
     * (pentru cleanup la nivel de socket), creează un SftpManager complet nou
     * și reconectează. SftpManager-ul vechi este abandonat.
     */
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

                connectWithRetries();
                return null;
            }
        };
    }

    public void disconnect() {
        if (!isConnected && sftpManager == null) return;

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

    public boolean isConnected() {
        return isConnected && sftpManager != null && sftpManager.isConnected();
    }

    /**
     * Notifică pierderea conexiunii. Thread-safe — poate fi apelat din orice thread
     * (ex: din monitoring thread-ul SftpManager-ului). Execuția callback-ului
     * se face pe JavaFX Application Thread.
     */
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

    /**
     * Returnează SftpManager-ul CURENT.
     * <p>
     * <b>IMPORTANT:</b> NU stocați această referință! La reconectare se creează
     * un SftpManager nou. Apelați getSftpManager() de fiecare dată.
     */
    public SftpManager getSftpManager() {
        return sftpManager;
    }

    public void setOnConnectionEstablished(Runnable callback) { this.onConnectionEstablished = callback; }
    public void setOnConnectionLost(Runnable callback) { this.onConnectionLost = callback; }
    public void setOnReconnectStarted(Runnable callback) { this.onReconnectStarted = callback; }
    public void setOnConnectionFailed(Consumer<String> callback) { this.onConnectionFailed = callback; }

    /**
     * Retry loop cu exponential backoff: 2s, 4s, 8s...
     * Dacă toate încercările eșuează, notifică prin onConnectionFailed și aruncă excepția.
     */
    private void connectWithRetries() throws Exception {
        Exception lastError = null;

        for (int attempt = 1; attempt <= CONNECT_MAX_RETRIES; attempt++) {
            try {
                doConnect();
                return;
            } catch (Exception e) {
                lastError = e;
                LOGGER.log(Level.SEVERE, "Connection attempt " + attempt + " failed", e);

                final String msg = "✗ Connection attempt " + attempt + " failed: " + e.getMessage();
                Platform.runLater(() -> log(msg));

                if (attempt < CONNECT_MAX_RETRIES) {
                    int backoffMs = (int) Math.pow(2, attempt - 1) * 2000;
                    Thread.sleep(backoffMs);
                }
            }
        }

        final String errMsg = lastError.getMessage();
        Platform.runLater(() -> {
            if (onConnectionFailed != null) onConnectionFailed.accept(errMsg);
        });
        throw lastError;
    }

    /**
     * Conectare efectivă: setează listener-ul de monitoring pe SftpManager,
     * deschide conexiunea, și notifică UI-ul prin callback.
     */
    private void doConnect() throws Exception {
        log("🔌 Connecting to server: " + server.getHost() + ":" + server.getPort());

        sftpManager.setConnectionStatusListener(this::notifyConnectionLost);
        sftpManager.connect();

        isConnected = true;

        Platform.runLater(() -> {
            log("✓ Successfully connected to server");
            log("✓ SFTP session established");
            if (onConnectionEstablished != null) {
                onConnectionEstablished.run();
            }
        });
    }

    private void log(String message) {
        logger.accept(message);
    }
}