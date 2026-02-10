package com.autodeploy.notification;

import com.autodeploy.domain.manager.RestartManager;
import com.autodeploy.domain.manager.RestartManager.RestartStatus;
import com.autodeploy.ui.dialogs.CustomAlert;
import javafx.application.Platform;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class RestartNotificationHandler {
    private final RestartManager restartManager;
    private final String currentUsername;
    private final Consumer<String> logger;

    private Consumer<RestartStatus> uiUpdateCallback;

    private NotificationController activeNotification;
    private String lastNotificationStatus = null;
    private long lastNotificationTime = 0;
    private static final long DEBOUNCE_MS = 1000;
    private Thread timerThread;

    // ✅ Previne notificări duplicate pentru același request
    private String lastShownPendingRequest = null;
    private NotificationController currentPendingNotification = null;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

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

    private void handleStatusChange(RestartStatus status) {
        if (status == null) {
            logger.accept("⚠ Warning: Received null status update");
            return;
        }

        if (status.getStatus() == null || status.getStatus().isEmpty()) {
            logger.accept("⚠ Warning: Status has null or empty status string");
            return;
        }

        String statusKey = status.getStatus() + "_" + status.getVersion() + "_" + status.getRequester();
        long now = System.currentTimeMillis();

        // Deduplicare cu time window
        if (statusKey.equals(lastNotificationStatus) &&
                (now - lastNotificationTime) < DEBOUNCE_MS) {
            // logger.accept("🔕 Duplicate notification suppressed: " + statusKey); // Optional debug
            return;
        }

        lastNotificationStatus = statusKey;
        lastNotificationTime = now;

        // ✅ NOTIFICĂ UI-ul MEREU (pentru timer și disabled state)
        if (uiUpdateCallback != null) {
            Platform.runLater(() -> uiUpdateCallback.accept(status));
        }

        // ✅ FORCE CLOSE: If status is NOT pending, kill any active 'Reject' or 'Request' popup immediately
        if (!"pending".equals(status.getStatus())) {
            dismissActiveNotification();
        }

        switch (status.getStatus()) {
            case "pending":
                handlePendingNotification(status);
                break;

            case "rejected":
                handleRejectedNotification(status);
                break;

            case "executing":
                handleExecutingNotification(status);
                break;

            case "completed": // ✅ CAZ NOU
                handleCompletedNotification(status);
                break;


            case "idle":
                dismissActiveNotification();
                break;

            default:
                logger.accept("⚠ Unknown status: " + status.getStatus());
                break;
        }
    }

    private void handlePendingNotification(RestartStatus status) {
        if (status.getRequester() == null) {
            logger.accept("⚠ Warning: Pending status has null requester");
            return;
        }

        String requestKey = "pending_" + status.getRequester() + "_" + status.getRequestedAt();

        if (requestKey.equals(lastShownPendingRequest)) {
            return;
        }

        lastShownPendingRequest = requestKey;

        dismissActiveNotification();

        boolean isRequester = currentUsername.equals(status.getRequester());

        Platform.runLater(() -> {
            if (isRequester) {
                // This uses your existing method which auto-closes after 2.5s
                currentPendingNotification = showRequesterNotification(status);
            } else {
                currentPendingNotification = showRejectNotification(status);
            }
        });
    }

    private NotificationController showRequesterNotification(RestartStatus status) {
        activeNotification = new NotificationController();

        String title = "🔄 Restart Request Sent";

        String projectName = getProjectDisplayName(status);
        String message = String.format("Pending approval for: %s\nAuto-approve in: %ss", projectName, status.getTimeRemaining());

        activeNotification.showSimpleNotification(title, message);

        return activeNotification;
    }

    /**
     * Notificare cu buton REJECT pentru ceilalți utilizatori
     */
    private NotificationController showRejectNotification(RestartStatus status) {
        activeNotification = new NotificationController();

        String requester = status.getRequester();
        String projectName = getProjectDisplayName(status);
        String message = String.format("%s wants to restart the server - %s!", requester, projectName);

        // Folosește notificare cu buton de acțiune
        activeNotification.showRestartServerNotification(
                message,
                () -> handleRejectAction(status)
        );

        logger.accept("⚠️ Restart request for " + projectName + " from " + requester + " - You can reject it!");

        return activeNotification; // ✅ Returnează referința
    }

    // Helper to safely get project name
    private String getProjectDisplayName(RestartStatus status) {
        String project = status.getProject();
        if (project == null || project.trim().isEmpty() || "null".equals(project)) {
            return "Unknown Project";
        }
        return project;
    }

    /**
     * Handler pentru acțiunea de reject
     */
    private void handleRejectAction(RestartStatus status) {
        try {
            restartManager.rejectRestart();
            logger.accept("🚫 You rejected the restart request from " + status.getRequester());

        } catch (Exception ex) {
            logger.accept("✗ Failed to reject restart: " + ex.getMessage());
            Platform.runLater(() -> {
                CustomAlert.showError("Reject Failed", ex.getMessage());
            });
        }
    }


    /**
     * Stop timer thread
     */
    private void stopTimerThread() {
        if (timerThread != null && timerThread.isAlive()) {
            timerThread.interrupt();
            timerThread = null;
        }
    }

    private void handleRejectedNotification(RestartStatus status) {
        lastShownPendingRequest = null; // ✅ Resetează
        currentPendingNotification = null; // ✅ Resetează
        // dismissActiveNotification() called in main switch

        // ✅ VERIFICARE rejections list
        if (status.getRejections() == null || status.getRejections().isEmpty()) {
            logger.accept("⚠ Warning: Rejected status has no rejections");
            return;
        }

        RestartStatus.Rejection lastRejection = status.getRejections()
                .get(status.getRejections().size() - 1);

        // ✅ VERIFICARE rejection user
        if (lastRejection.getUser() == null) {
            logger.accept("⚠ Warning: Rejection has null user");
            return;
        }

        String rejectorName = lastRejection.getUser();
        boolean isRequester = status.getRequester() != null &&
                currentUsername.equals(status.getRequester());

        String projectName = getProjectDisplayName(status);

        Platform.runLater(() -> {
            // Create NEW instance
            activeNotification = new NotificationController();

            if (isRequester) {
                // Notificare IMPORTANTĂ pentru requester
                activeNotification.showSimpleNotification(
                        "🚫 Restart Rejected",
                        "Your restart request on " + projectName + " was rejected by " + rejectorName
                );
                logger.accept("🚫 Restart request rejected by " + rejectorName);

            } else if (!currentUsername.equals(rejectorName)) {
                // Notificare simplă pentru ceilalți (nu pentru cel care a dat reject)
                String requesterName = status.getRequester() != null ? status.getRequester() : "unknown";
                activeNotification.showSimpleNotification(
                        "🚫 Restart Rejected",
                        rejectorName + " declined the " + projectName + " server restart initiated by " + requesterName
                );
                logger.accept("🚫 " + rejectorName + " rejected restart from " + requesterName);
            }
        });
    }

    private void handleExecutingNotification(RestartStatus status) {
        lastShownPendingRequest = null; // ✅ Resetează
        currentPendingNotification = null; // ✅ Resetează
        // dismissActiveNotification() called in main switch

        String requesterName = status.getRequester() != null ? status.getRequester() : "unknown";
        String projectName = getProjectDisplayName(status);

        Platform.runLater(() -> {
            // ✅ FIX: Atribuim instanța creată variabilei activeNotification
            activeNotification = new NotificationController();
            activeNotification.showSimpleNotification(
                    "🔄 The Server is Restarting",
                    "Target: " + projectName + " - initiated by: " + requesterName
            );
        });

        logger.accept("🔄 " + projectName + " is restarting - initiated by " + requesterName);
    }

    /**
     * ✅ METODA NOUĂ: Notificare de succes
     */
    private void handleCompletedNotification(RestartStatus status) {
        lastShownPendingRequest = null;
        currentPendingNotification = null;
        // dismissActiveNotification() called in main switch

        String requesterName = status.getRequester() != null ? status.getRequester() : "unknown";
        String projectName = getProjectDisplayName(status);

        Platform.runLater(() -> {
            activeNotification = new NotificationController();
            activeNotification.showSimpleNotification(
                    "✅ Restart Completed",
                    "The restart finished successfully.\nGood job, " + requesterName + "!"
            );
        });

        logger.accept("✅ " + projectName + " restart completed successfully - initiated by " + requesterName);
    }

    private void dismissActiveNotification() {
        stopTimerThread();

        // Capture reference to avoid race conditions
        NotificationController notificationToClose = activeNotification;

        if (notificationToClose != null) {
            Platform.runLater(() -> {
                try {
                    notificationToClose.close();
                } catch (Exception e) {
                    // Ignore
                }
            });
        }

        activeNotification = null;
        currentPendingNotification = null;
    }

    private void logStatusChange(RestartStatus status) {
        String time = TIME_FORMATTER.format(Instant.ofEpochSecond(status.getLastUpdate()));

        logger.accept("--------------------------------------");
        logger.accept("📊 Restart Status Update [" + time + "]");
        logger.accept("   Status: " + status.getStatus().toUpperCase());

        // Log project name
        logger.accept("   Project: " + getProjectDisplayName(status));

        if (status.getRequester() != null) {
            logger.accept("   Requester: " + status.getRequester());
        }

        if (status.isPending()) {
            logger.accept("   Time remaining: " + status.getTimeRemaining() + "s");
        }

        if (status.getRejections() != null && !status.getRejections().isEmpty()) {
            logger.accept("   Rejections: " + status.getRejections().size());
            for (RestartStatus.Rejection rejection : status.getRejections()) {
                if (rejection.getUser() != null) {
                    logger.accept("      - " + rejection.getUser());
                }
            }
        }

        logger.accept("--------------------------------------");
    }

    public void shutdown() {
        dismissActiveNotification();
    }
}