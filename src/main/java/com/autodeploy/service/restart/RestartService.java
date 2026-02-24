package com.autodeploy.service.restart;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.domain.manager.RestartManager;
import com.autodeploy.domain.model.RestartStatus;
import com.autodeploy.infrastructure.connection.ConnectionManager;
import com.autodeploy.notification.RestartNotificationHandler;
import com.autodeploy.domain.model.Server;
import javafx.concurrent.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Fațadă (Facade) peste {@link RestartManager} și {@link RestartNotificationHandler}.
 * <p>
 * Simplifică interacțiunea UI-ului cu sistemul de restart:
 * <ul>
 *   <li>Inițializare lazy — restartManager + notificationHandler sunt create la {@link #initialize()},
 *       nu în constructor (depind de conexiunea activă)</li>
 *   <li>Polling management — start/stop cu interval fix de 2s</li>
 *   <li>Timer UI — formatează timpul scurs bazat pe {@code active_restart.started_at} de pe server</li>
 *   <li>Listener buffering — listener-ii adăugați înainte de initialize() sunt reținuți
 *       și atașați automat după inițializare</li>
 * </ul>
 * <p>
 * <b>Notă despre timer:</b> Timpul de restart se bazează exclusiv pe timestamp-ul serverului
 * ({@code active_restart.started_at}), NU pe un timer local. Astfel timerul rămâne corect
 * chiar dacă aplicația e repornită în timpul unui restart.
 */
public class RestartService {

    private final Server server;
    private final ConnectionManager connectionManager;
    private final Consumer<String> logger;

    private RestartManager restartManager;
    private RestartNotificationHandler notificationHandler;

    /** Ultimul status primit de la server — folosit de timer și isRestarting(). */
    private volatile RestartStatus latestStatus = null;

    /**
     * Listener-i adăugați înainte de initialize().
     * RestartManager nu există încă, deci îi reținem temporar
     * și îi atașăm în {@link #attachPendingListeners()}.
     */
    private final List<Consumer<RestartStatus>> pendingListeners = new ArrayList<>();

    public RestartService(Server server, ConnectionManager connectionManager, Consumer<String> logger) {
        this.server = server;
        this.connectionManager = connectionManager;
        this.logger = logger;
    }

    /**
     * Inițializare lazy — creează RestartManager și NotificationHandler.
     * Separat de constructor deoarece depinde de conexiunea activă și de username-ul rezolvat.
     *
     * @return true dacă inițializarea a reușit
     */
    public boolean initialize() {
        try {
            String currentUser = resolveCurrentUser();

            this.restartManager = new RestartManager(server, connectionManager, currentUser);
            this.notificationHandler = new RestartNotificationHandler(restartManager, currentUser, logger);

            notificationHandler.setUiUpdateCallback(this::handleStatusUpdate);
            attachPendingListeners();

            return true;
        } catch (Exception e) {
            logger.accept("Failed to initialize Restart Service: " + e.getMessage());
            return false;
        }
    }

    public void startPolling() {
        if (restartManager != null) {
            restartManager.startPolling(2000);
        }
    }

    public void stopPolling() {
        if (restartManager != null) {
            restartManager.stopPolling();
        }
    }

    /**
     * Adaugă un listener pentru schimbări de status.
     * Dacă RestartManager nu e încă inițializat, listener-ul e buffered
     * și atașat automat la {@link #initialize()}.
     */
    public void addStatusListener(Consumer<RestartStatus> listener) {
        if (restartManager != null) {
            restartManager.addListener(listener);
        } else {
            synchronized (pendingListeners) {
                pendingListeners.add(listener);
            }
        }
    }

    public Task<RestartStatus> requestRestartAsync(String projectName) {
        return new Task<>() {
            @Override
            protected RestartStatus call() throws Exception {
                return restartManager.requestRestart(projectName);
            }
        };
    }

    /**
     * Returnează timpul scurs de la începutul restartului, formatat ca (MM:SS).
     * <p>
     * Se bazează exclusiv pe {@code active_restart.started_at} din JSON-ul serverului,
     * NU pe un timer local. Astfel:
     * <ul>
     *   <li>Timerul persistă între reporniri ale aplicației</li>
     *   <li>Timerul e corect chiar dacă statusul cererii devine "rejected"
     *       (restartul fizic continuă independent)</li>
     *   <li>Nu există drift între client și server</li>
     * </ul>
     *
     * @return "(MM:SS)" sau "" dacă nu există restart activ
     */
    public String getFormattedElapsedTime() {
        if (latestStatus == null || !latestStatus.hasActiveRestart()) {
            return "";
        }

        long startedAtEpoch = latestStatus.getActiveRestart().getStartedAt();
        long elapsedMillis = System.currentTimeMillis() - (startedAtEpoch * 1000);

        if (elapsedMillis < 0) elapsedMillis = 0;

        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);
        long minutes = seconds / 60;
        seconds = seconds % 60;

        return String.format("(%02d:%02d)", minutes, seconds);
    }

    /**
     * Serverul este în restart dacă {@code active_restart} există pe server.
     * Independent de statusul cererii (poate fi "rejected" dar restartul fizic continuă).
     */
    public boolean isRestarting() {
        return latestStatus != null && latestStatus.hasActiveRestart();
    }

    public void shutdown() {
        stopPolling();
        if (notificationHandler != null) {
            notificationHandler.shutdown();
        }
    }

    /**
     * Callback de la NotificationHandler — salvează ultimul status.
     * Timer-ul și isRestarting() folosesc direct acest status.
     * Toată logica de timp se bazează pe active_restart.started_at din JSON,
     * fără manipulare locală de timestamp-uri.
     */
    private void handleStatusUpdate(RestartStatus status) {
        this.latestStatus = status;
    }

    private void attachPendingListeners() {
        synchronized (pendingListeners) {
            for (Consumer<RestartStatus> listener : pendingListeners) {
                restartManager.addListener(listener);
            }
            pendingListeners.clear();
        }
    }

    /**
     * Rezolvă username-ul curent: preferă cel din config (setat manual de user),
     * fallback pe system property (user.name) dacă nu e configurat.
     */
    private String resolveCurrentUser() {
        String configUser = ApplicationConfig.getInstance().getUsername();
        if (configUser != null && !configUser.trim().isEmpty()
                && !"null".equalsIgnoreCase(configUser.trim())) {
            return configUser.trim();
        }
        return System.getProperty("user.name");
    }

    public Long getStatusActiveStartedAt() {
        if (latestStatus != null && latestStatus.getActiveRestart() != null) {
            return latestStatus.getActiveRestart().getStartedAt();
        }
        return null;
    }

    public Long getStatusActiveRequestedAt() {
        if (latestStatus != null && latestStatus.getActiveRestart() != null) {
            return latestStatus.getActiveRestart().getRequestedAt();
        }
        return null;
    }
}