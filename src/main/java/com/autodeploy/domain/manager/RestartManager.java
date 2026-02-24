package com.autodeploy.domain.manager;

import com.autodeploy.domain.model.RestartStatus;
import com.autodeploy.domain.model.Server;
import com.autodeploy.infrastructure.connection.ConnectionManager;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionează comunicarea cu scriptul de restart de pe server.
 * <p>
 * Funcționalitate:
 * <ul>
 *   <li>Execută comenzi remote (request, reject, get status)</li>
 *   <li>Polling periodic cu circuit-breaker (se oprește după N erori consecutive)</li>
 *   <li>Notifică listener-ii pe JavaFX Application Thread la schimbări de status</li>
 * </ul>
 */
public class RestartManager {

    private static final Logger LOGGER = Logger.getLogger(RestartManager.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Delay între scrierea fișierului de status pe server și citirea lui.
     * Necesar deoarece scriptul bash scrie asincron pe filesystem.
     */
    private static final int STATUS_FETCH_DELAY_MS = 200;

    /**
     * Numărul maxim de erori consecutive de polling înainte de oprire automată.
     * Previne logging infinit pe o conexiune moartă.
     */
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    private final Server server;
    private final ConnectionManager connectionManager;
    private final String currentUsername;

    private RestartStatus lastStatus;
    private Thread pollingThread;
    private volatile boolean polling = false;
    private final List<Consumer<RestartStatus>> listeners = new CopyOnWriteArrayList<>();

    public RestartManager(Server server, ConnectionManager connectionManager, String currentUsername) {
        this.server = server;
        this.connectionManager = connectionManager;
        this.currentUsername = currentUsername;
    }

    public RestartStatus getStatus() throws Exception {
        String command = buildCommand("get");
        String output = executeCommand(command);
        return parseStatusResponse(output);
    }

    public RestartStatus requestRestart(String projectName) throws Exception {
        String safeProjectName = sanitize(projectName);
        String command = String.format("%s %s request \"%s\"",
                server.getRestartManagerScript(), currentUsername, safeProjectName);

        LOGGER.info("Executing restart request command");
        String output = executeCommand(command);

        return handleCommandResponse(output);
    }

    public RestartStatus rejectRestart() throws Exception {
        String command = buildCommand("reject");

        LOGGER.info("Executing reject command");
        String output = executeCommand(command);

        if (output != null && output.startsWith("ERROR:")) {
            throw new RuntimeException(output.replace("ERROR:", "").trim());
        }

        LOGGER.info("Reject executed, refreshing status...");
        Thread.sleep(STATUS_FETCH_DELAY_MS);
        return getStatus();
    }

    public void startPolling(int intervalMs) {
        if (polling) return;

        polling = true;
        pollingThread = new Thread(() -> pollLoop(intervalMs), "RestartManager-Polling");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    public void stopPolling() {
        polling = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            pollingThread = null;
        }
    }

    /**
     * Polling loop cu circuit-breaker: se oprește automat după
     * {@link #MAX_CONSECUTIVE_ERRORS} erori consecutive.
     * Notifică listener-ii doar la schimbări efective de status.
     */
    private void pollLoop(int intervalMs) {
        int consecutiveErrors = 0;

        while (polling) {
            try {
                RestartStatus newStatus = getStatus();

                if (newStatus != null && newStatus.hasChangedFrom(lastStatus)) {
                    final RestartStatus statusToNotify = newStatus;
                    Platform.runLater(() -> notifyListeners(statusToNotify));
                    lastStatus = newStatus;
                }

                consecutiveErrors = 0;
                Thread.sleep(intervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                consecutiveErrors++;

                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    LOGGER.warning("Polling stopped after " + consecutiveErrors
                            + " consecutive errors. Last: " + e.getMessage());
                    break;
                }

                LOGGER.log(Level.FINE, "Polling error (" + consecutiveErrors
                        + "/" + MAX_CONSECUTIVE_ERRORS + ")", e);

                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void addListener(Consumer<RestartStatus> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<RestartStatus> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(RestartStatus status) {
        for (Consumer<RestartStatus> listener : listeners) {
            try {
                listener.accept(status);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in restart status listener", e);
            }
        }
    }

    private String executeCommand(String command) throws Exception {
        return connectionManager.getSftpManager().executeCommand(command);
    }

    private String buildCommand(String action) {
        return server.getRestartManagerScript() + " " + currentUsername + " " + action;
    }

    private RestartStatus parseStatusResponse(String output) {
        if (output == null || output.trim().isEmpty()) {
            return null;
        }

        String trimmed = output.trim();

        if (trimmed.startsWith("ERROR:")) {
            LOGGER.warning("Error from restart script: " + trimmed);
            return null;
        }

        if (!trimmed.startsWith("{")) {
            LOGGER.fine("Non-JSON response from restart script: " + trimmed);
            return null;
        }

        try {
            return MAPPER.readValue(trimmed, RestartStatus.class);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse restart status JSON", e);
            return null;
        }
    }

    /**
     * Procesează răspunsul de la comenzile request/reject.
     * Protocolul cu scriptul bash:
     * <ul>
     *   <li>"ERROR: ..." → excepție</li>
     *   <li>"OK: ..." → comandă acceptată, fetch status actualizat</li>
     *   <li>JSON → parsare directă</li>
     *   <li>Altceva / gol → fallback pe getStatus()</li>
     * </ul>
     */
    private RestartStatus handleCommandResponse(String output) throws Exception {
        if (output == null || output.trim().isEmpty()) {
            LOGGER.info("Empty response from request, fetching status...");
            Thread.sleep(STATUS_FETCH_DELAY_MS);
            return getStatus();
        }

        String trimmed = output.trim();

        if (trimmed.startsWith("ERROR:")) {
            throw new RuntimeException(trimmed.replace("ERROR:", "").trim());
        }

        if (trimmed.startsWith("OK:")) {
            LOGGER.info("Request acknowledged: " + trimmed);
            Thread.sleep(STATUS_FETCH_DELAY_MS);
            return getStatus();
        }

        if (trimmed.startsWith("{")) {
            RestartStatus parsed = parseStatusResponse(trimmed);
            return parsed != null ? parsed : getStatus();
        }

        return getStatus();
    }

    /**
     * Sanitizează input-ul pentru a fi safe ca argument shell.
     * Permite doar caractere alfanumerice, spații, cratime, underscore și punct.
     */
    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9 _\\-.]", "");
    }
}