package com.autodeploy.ui.window.component;

import com.autodeploy.domain.model.Server;
import com.autodeploy.infrastructure.connection.ConnectionManager;
import com.autodeploy.service.restart.RestartService;
import com.autodeploy.ui.overlay.UIOverlayManager;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Orchestrează conexiunea SFTP și overlay-urile UI asociate.
 * <p>
 * Face legătura între {@link ConnectionManager} (logica de conexiune) și
 * {@link UIOverlayManager} (feedback vizual), gestionând tranzițiile:
 * <ul>
 *   <li>Connect: overlay loading → succes (hide) / fail (overlay cu Try Again)</li>
 *   <li>Connection lost: overlay reconnect (Reconnect / Close)</li>
 *   <li>Reconnect: overlay loading → succes / fail (ciclul se repetă)</li>
 * </ul>
 * <p>
 * La fiecare connect/reconnect reușit, inițializează și pornește {@link RestartService}.
 * La fiecare disconnect/connection lost, oprește polling-ul de restart.
 * <p>
 * Flag-ul {@code isConnecting} previne afișarea overlay-ului "Connection Lost"
 * în timpul unei reconectări active (care ar suprascrie overlay-ul de loading).
 */
public class ConnectionHandler {

    private static final Logger LOGGER = Logger.getLogger(ConnectionHandler.class.getName());

    private final Server server;
    private final ConnectionManager connectionManager;
    private final UIOverlayManager overlayManager;
    private final Consumer<String> logger;

    private RestartService restartService;
    private Runnable onConnected;
    private Runnable onDisconnected;
    private Runnable onReturnToSelection;

    /**
     * Previne overlay-ul "Connection Lost" în timpul unei reconectări active.
     * Fără acest flag, pierderea temporară de conexiune la reconnect ar afișa
     * overlay-ul de "Connection Lost" peste cel de "Reconnecting...".
     */
    private boolean isConnecting = false;

    public ConnectionHandler(Server server, ConnectionManager connectionManager,
                             UIOverlayManager overlayManager, Consumer<String> logger) {
        this.server = server;
        this.connectionManager = connectionManager;
        this.overlayManager = overlayManager;
        this.logger = logger;
    }

    public void setRestartService(RestartService restartService) {
        this.restartService = restartService;
    }

    public void setOnConnected(Runnable callback) { this.onConnected = callback; }
    public void setOnDisconnected(Runnable callback) { this.onDisconnected = callback; }
    public void setOnReturnToSelection(Runnable callback) { this.onReturnToSelection = callback; }

    /**
     * Configurează toate callback-urile pe ConnectionManager.
     * Trebuie apelat o singură dată, la inițializarea ferestrei.
     * <p>
     * Fluxul de stări:
     * <pre>
     * connect() → onConnectionEstablished → hide overlay, start polling
     *           → onConnectionFailed → show failure overlay
     *
     * [connected] → onConnectionLost → stop polling, show reconnect overlay
     *             → reconnect() → onReconnectStarted → show loading overlay
     *                           → onConnectionEstablished → hide overlay, restart polling
     *                           → onConnectionFailed → show failure overlay
     * </pre>
     */
    public void setupCallbacks() {
        connectionManager.setOnConnectionEstablished(() -> {
            if (isConnecting) {
                overlayManager.hideOverlay();
                isConnecting = false;
            }
            if (onConnected != null) onConnected.run();
            if (restartService != null && restartService.initialize()) {
                restartService.startPolling();
            }
        });

        connectionManager.setOnConnectionLost(() -> {
            if (restartService != null) restartService.stopPolling();
            if (!isConnecting) {
                if (onDisconnected != null) onDisconnected.run();
                showReconnectOverlay();
            }
        });

        connectionManager.setOnReconnectStarted(() -> {
            isConnecting = true;
            overlayManager.showLoadingOverlay("Reconnecting to Server...",
                    serverDisplayName());
        });

        connectionManager.setOnConnectionFailed(errorMessage -> {
            isConnecting = false;
            overlayManager.showReconnectFailure(
                    "Connection Failed", errorMessage,
                    this::reconnect,
                    onReturnToSelection
            );
        });
    }

    public void connect() {
        isConnecting = true;
        overlayManager.showLoadingOverlay("Connecting to Server...", serverDisplayName());
        if (onDisconnected != null) onDisconnected.run();

        AsyncHelper.runVirtual(connectionManager.connectAsync(), "SFTP-Connection");
    }

    public void reconnect() {
        logger.accept("🔄 Attempting to reconnect...");
        isConnecting = true;
        overlayManager.showLoadingOverlay("Reconnecting...",
                "Attempting to reach " + server.getHost());

        var reconnectTask = connectionManager.reconnectAsync();
        reconnectTask.setOnFailed(event -> {
            isConnecting = false;
            String errorMsg = reconnectTask.getException() != null
                    ? reconnectTask.getException().getMessage()
                    : "Unknown Error";

            overlayManager.showReconnectFailure(
                    "Reconnection Failed", errorMsg,
                    this::reconnect,
                    onReturnToSelection
            );
        });

        AsyncHelper.runDaemon(reconnectTask, "SFTP-Reconnect");
    }

    /**
     * Deconectare curată: dezactivează callback-ul de "Connection Lost" ÎNAINTE
     * de a deconecta, altfel disconnect() ar triggera overlay-ul de reconnect.
     */
    public void disconnect() {
        connectionManager.setOnConnectionLost(null);
        connectionManager.disconnect();
    }

    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    public void notifyConnectionLost() {
        connectionManager.notifyConnectionLost();
    }

    private void showReconnectOverlay() {
        overlayManager.showReconnectOverlay(
                server.getName(), server.getHost(),
                this::reconnect,
                onReturnToSelection
        );
    }

    private String serverDisplayName() {
        return server.getName() + " (" + server.getHost() + ")";
    }
}