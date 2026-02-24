package com.autodeploy.notification;

import com.autodeploy.domain.manager.RestartManager;
import com.autodeploy.domain.model.RestartStatus;
import com.autodeploy.ui.dialog.CustomAlert;
import javafx.application.Platform;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionează notificările legate de restart server.
 * Se înregistrează ca listener la {@link RestartManager} și transformă
 * schimbările de status în notificări vizuale + mesaje de log.
 * <p>
 * Logica de notificare e diferențiată pe roluri:
 * <ul>
 *   <li><b>Requester</b> (cel care a cerut restartul) — primește confirmări/rejecții</li>
 *   <li><b>Ceilalți utilizatori</b> — primesc notificări cu opțiunea de a da reject</li>
 * </ul>
 * <p>
 * Mecanisme anti-spam:
 * <ul>
 *   <li><b>Debounce</b> — ignoră statusuri identice primite la mai puțin de {DEBOUNCE_MS}ms</li>
 *   <li><b>Pending dedup</b> — un request pending cu același requester+timestamp nu se notifică de 2 ori</li>
 *   <li><b>Executing dedup</b> — un restart executing cu același requester+requestedAt nu re-notifică
 *       (important când polling-ul returnează repeated "executing" status)</li>
 *   <li><b>Override detection</b> — detectează când un request nou înlocuiește unul existent</li>
 * </ul>
 */
public class RestartNotificationHandler {

    private static final Logger LOGGER = Logger.getLogger(RestartNotificationHandler.class.getName());

    /** Interval minim între două notificări cu aceeași cheie (status + update + requester). */
    private static final long DEBOUNCE_MS = 1000;

    private final RestartManager restartManager;
    private final String currentUsername;
    private final Consumer<String> logger;

    /** Callback pentru actualizarea UI-ului (ex: butoane, labels în DeploymentWindow). */
    private Consumer<RestartStatus> uiUpdateCallback;

    /** Notificarea curentă afișată. Maxim una activă la un moment dat. */
    private NotificationController activeNotification;

    // --- State pentru deduplicare ---
    private String lastStatusKey = null;
    private long lastStatusTime = 0;
    /** Cheia ultimului pending notificat — previne re-afișarea aceluiași request. */
    private String lastShownPendingKey = null;

    // --- State pentru override detection ---
    /** Status-ul anterior — folosit pentru a detecta tranziția pending→pending (override). */
    private String previousStatus = null;
    private String previousRequester = null;

    // --- State pentru executing deduplication ---
    /** Ultimul restart executing notificat. Previne re-notificarea la fiecare poll. */
    private String lastExecutingRequester = null;
    private Long lastExecutingRequestedAt = null;

    public RestartNotificationHandler(RestartManager restartManager,
                                      String currentUsername,
                                      Consumer<String> logger) {
        this.restartManager = restartManager;
        this.currentUsername = currentUsername;
        this.logger = logger;

        restartManager.addListener(this::handleStatusChange);
    }

    public void setUiUpdateCallback(Consumer<RestartStatus> callback) {
        this.uiUpdateCallback = callback;
    }

    public void shutdown() {
        dismissActiveNotification();
    }

    /**
     * Punct central de procesare a schimbărilor de status.
     * <p>
     * Fluxul:
     * <ol>
     *   <li>Validare + debounce</li>
     *   <li>Propagare către UI callback (dacă există)</li>
     *   <li>Override detection (request nou peste unul existent)</li>
     *   <li>Dismiss notificarea veche (dacă status != pending)</li>
     *   <li>Dispatch pe tipul de status</li>
     *   <li>Actualizare state intern (previousStatus, previousRequester)</li>
     * </ol>
     */
    private void handleStatusChange(RestartStatus status) {
        if (status == null || status.getStatus() == null || status.getStatus().isEmpty()) {
            LOGGER.warning("Received invalid status update");
            return;
        }

        if (isDuplicate(status)) return;

        if (uiUpdateCallback != null) {
            Platform.runLater(() -> uiUpdateCallback.accept(status));
        }

        boolean isOverride = detectOverride(status);

        if (!status.isPending()) {
            dismissActiveNotification();
        }

        switch (status.getStatus().toLowerCase()) {
            case "pending"   -> handlePending(status, isOverride);
            case "rejected"  -> handleRejected(status);
            case "executing" -> {
                boolean isNewExecution = lastExecutingRequester == null
                        || lastExecutingRequestedAt == null
                        || !status.getRequester().equals(lastExecutingRequester)
                        || !status.getRequestedAt().equals(lastExecutingRequestedAt);

                if (isNewExecution) {
                    handleExecuting(status);
                    lastExecutingRequester = status.getRequester();
                    lastExecutingRequestedAt = status.getRequestedAt();
                }
            }
            case "completed" -> handleCompleted(status);
            case "idle"      -> dismissActiveNotification();
            default          -> LOGGER.warning("Unknown status: " + status.getStatus());
        }

        previousStatus = status.getStatus();
        previousRequester = status.getRequester();
    }

    /**
     * Gestionează statusul "pending".
     * <p>
     * Comportament diferențiat:
     * <ul>
     *   <li><b>Requester:</b> Notificare simplă de confirmare (cu countdown)</li>
     *   <li><b>Alți useri:</b> Notificare importantă cu buton "Reject" (fără auto-close)</li>
     * </ul>
     * <p>
     * Dacă {@code isOverride} e true, mesajul indică explicit că un request anterior
     * a fost înlocuit (override = pending nou peste pending/executing existent).
     */
    private void handlePending(RestartStatus status, boolean isOverride) {
        if (status.getRequester() == null) return;

        String pendingKey = "pending_" + status.getRequester() + "_" + status.getRequestedAt();
        if (pendingKey.equals(lastShownPendingKey)) return;
        lastShownPendingKey = pendingKey;

        dismissActiveNotification();

        boolean isRequester = currentUsername.equals(status.getRequester());
        String project = getProjectName(status);

        Platform.runLater(() -> {
            activeNotification = new NotificationController();

            if (isRequester) {
                String title = isOverride
                        ? "🔄 Restart Override Sent"
                        : "🔄 Restart Request Sent";
                String message = isOverride
                        ? String.format("New restart request for: %s\nPrevious request replaced.\nAuto-approve in: %ss",
                        project, status.getTimeRemaining())
                        : String.format("Pending approval for: %s\nAuto-approve in: %ss",
                        project, status.getTimeRemaining());

                activeNotification.showSimpleNotification(title, message);
            } else {
                String requester = status.getRequester();
                String message = isOverride
                        ? String.format("%s overrode the previous restart - %s!\nNew 30s approval window started.",
                        requester, project)
                        : String.format("%s wants to restart the server - %s!",
                        requester, project);

                activeNotification.showRestartServerNotification(
                        message,
                        () -> executeReject(status));

                String logMsg = isOverride
                        ? "⚠️ " + requester + " overrode restart for " + project + " - You can reject!"
                        : "⚠️ Restart request for " + project + " from " + requester + " - You can reject it!";
                logger.accept(logMsg);
            }
        });
    }

    /**
     * Gestionează statusul "rejected".
     * Nu afișează notificare celui care a dat reject (el deja știe).
     * Requester-ul primește notificare că i-a fost respinsă cererea.
     */
    private void handleRejected(RestartStatus status) {
        resetPendingState();

        if (status.getRejections() == null || status.getRejections().isEmpty()) return;

        RestartStatus.Rejection lastRejection = status.getRejections()
                .get(status.getRejections().size() - 1);
        if (lastRejection.getUser() == null) return;

        String rejector = lastRejection.getUser();
        String project = getProjectName(status);
        boolean isRequester = currentUsername.equals(status.getRequester());

        if (currentUsername.equals(rejector)) return;

        Platform.runLater(() -> {
            activeNotification = new NotificationController();

            if (isRequester) {
                activeNotification.showSimpleNotification(
                        "🚫 Restart Rejected",
                        "Your restart request on " + project + " was rejected by " + rejector);
                logger.accept("🚫 Restart request rejected by " + rejector);
            } else {
                String requester = status.getRequester() != null ? status.getRequester() : "unknown";
                activeNotification.showSimpleNotification(
                        "🚫 Restart Rejected",
                        rejector + " declined the " + project
                                + " server restart initiated by " + requester);
                logger.accept("🚫 " + rejector + " rejected restart from " + requester);
            }
        });
    }

    /**
     * Gestionează statusul "executing".
     * Notifică doar pentru restart-uri NOI — ignoră poll-urile repetate
     * cu același requester + requestedAt (deduplicat în handleStatusChange).
     */
    private void handleExecuting(RestartStatus status) {
        resetPendingState();

        String requester = status.getRequester() != null ? status.getRequester() : "unknown";
        String project = getProjectName(status);

        Platform.runLater(() -> {
            activeNotification = new NotificationController();
            activeNotification.showSimpleNotification(
                    "🔄 The Server is Restarting",
                    "Target: " + project + " - initiated by: " + requester);
        });
        logger.accept("🔄 " + project + " is restarting - initiated by " + requester);
    }

    private void handleCompleted(RestartStatus status) {
        resetPendingState();

        String requester = status.getRequester() != null ? status.getRequester() : "unknown";
        String project = getProjectName(status);

        Platform.runLater(() -> {
            activeNotification = new NotificationController();
            activeNotification.showSimpleNotification(
                    "✅ Restart Completed",
                    "The restart finished successfully.\nGood job, " + requester + "!");
        });

        logger.accept("✅ " + project + " restart completed successfully - initiated by " + requester);
    }

    /**
     * Detectează dacă noul status e un override (request nou peste unul existent).
     * Override = statusul anterior era pending sau executing, iar cel nou e pending
     * cu un timestamp diferit (deci e o cerere complet nouă, nu aceeași).
     */
    private boolean detectOverride(RestartStatus status) {
        if (!status.isPending()) return false;
        if (previousStatus == null) return false;

        return "pending".equals(previousStatus) || "executing".equals(previousStatus);
    }

    private void executeReject(RestartStatus status) {
        try {
            restartManager.rejectRestart();
            logger.accept("🚫 You rejected the restart request from " + status.getRequester());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to reject restart", ex);
            logger.accept("✗ Failed to reject restart: " + ex.getMessage());
            Platform.runLater(() -> CustomAlert.showError("Reject Failed", ex.getMessage()));
        }
    }

    /**
     * Debounce: ignoră statusuri identice (aceeași cheie) primite în interval
     * mai mic de {DEBOUNCE_MS}ms. Previne notificări duplicate când polling-ul
     * returnează același status de mai multe ori în succesiune rapidă.
     */
    private boolean isDuplicate(RestartStatus status) {
        String key = status.getStatus() + "_" + status.getLastUpdate() + "_" + status.getRequester();
        long now = System.currentTimeMillis();

        if (key.equals(lastStatusKey) && (now - lastStatusTime) < DEBOUNCE_MS) {
            return true;
        }

        lastStatusKey = key;
        lastStatusTime = now;
        return false;
    }

    private void resetPendingState() {
        lastShownPendingKey = null;
    }

    /**
     * Închide notificarea activă pe JavaFX thread.
     * Maxim o notificare e vizibilă la un moment dat — cea nouă o înlocuiește pe cea veche.
     */
    private void dismissActiveNotification() {
        NotificationController toClose = activeNotification;
        activeNotification = null;

        if (toClose != null) {
            Platform.runLater(() -> {
                try { toClose.close(); }
                catch (Exception ignored) {}
            });
        }
    }

    private String getProjectName(RestartStatus status) {
        String project = status.getProject();
        return (project == null || project.trim().isEmpty() || "null".equals(project))
                ? "Unknown Project" : project;
    }
}