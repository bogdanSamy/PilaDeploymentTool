package com.autodeploy.service.restart;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.domain.manager.RestartManager;
import com.autodeploy.domain.manager.RestartManager.RestartStatus;
import com.autodeploy.notification.RestartNotificationHandler;
import com.autodeploy.domain.model.Server;
import com.autodeploy.infrastructure.sftp.SftpManager;
import javafx.concurrent.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RestartService {
    private final Server server;
    private final SftpManager sftpManager;
    private final Consumer<String> logger;

    private RestartManager restartManager;
    private RestartNotificationHandler notificationHandler;

    private Consumer<Boolean> buttonStateCallback;
    private Runnable onRestartStarted;
    private Runnable onRestartCompleted;
    private long restartStartTime = 0;
    private String originalButtonText = "Restart Server";

    // ✅ FIX 1: A list to hold listeners that try to register before we are ready
    private final List<Consumer<RestartStatus>> pendingListeners = new ArrayList<>();

    public RestartService(Server server, SftpManager sftpManager, Consumer<String> logger) {
        this.server = server;
        this.sftpManager = sftpManager;
        this.logger = logger;
    }

    public boolean initialize() {
        try {
            // Read username from application configuration
            String currentUser = ApplicationConfig.getInstance().getUsername();
            if (currentUser == null || currentUser.trim().isEmpty() || "null".equalsIgnoreCase(currentUser.trim())) {
                // Fallback to OS user if config is missing or placeholder
                currentUser = System.getProperty("user.name");
            }

            this.restartManager = new RestartManager(server, sftpManager, currentUser);
            this.notificationHandler = new RestartNotificationHandler(restartManager, currentUser, logger);

            notificationHandler.setUiUpdateCallback(this::handleStatusUpdate);

            // ✅ FIX 2: Attach the saved listeners now that restartManager exists
            synchronized (pendingListeners) {
                for (Consumer<RestartStatus> listener : pendingListeners) {
                    restartManager.addListener(listener);
                }
                pendingListeners.clear();
            }

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

    public void setOnButtonStateChanged(Consumer<Boolean> callback) {
        this.buttonStateCallback = callback;
    }

    public void setOnRestartStarted(Runnable callback) {
        this.onRestartStarted = callback;
    }

    public void setOnRestartCompleted(Runnable callback) {
        this.onRestartCompleted = callback;
    }

    /**
     * ✅ FIX 3: If manager is null, save the listener for later!
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

    public void removeStatusListener(Consumer<RestartStatus> listener) {
        if (restartManager != null) {
            restartManager.removeListener(listener);
        } else {
            synchronized (pendingListeners) {
                pendingListeners.remove(listener);
            }
        }
    }

    private void handleStatusUpdate(RestartStatus status) {
        if (status == null) return;

        boolean isBusy = status.isPending() || status.isExecuting() || status.isApproved();

        if (buttonStateCallback != null) {
            buttonStateCallback.accept(isBusy);
        }

        if (status.isExecuting()) {
            if (restartStartTime == 0) {
                restartStartTime = System.currentTimeMillis();
                if (onRestartStarted != null) onRestartStarted.run();
            }
        } else if (status.isCompleted() || status.isIdle() || status.isRejected()) {
            if (restartStartTime != 0) {
                restartStartTime = 0;
                if (onRestartCompleted != null) onRestartCompleted.run();
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

    public String getFormattedElapsedTime() {
        if (restartStartTime == 0) return "";

        long elapsedMillis = System.currentTimeMillis() - restartStartTime;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);
        long minutes = seconds / 60;
        seconds = seconds % 60;

        return String.format("(%02d:%02d)", minutes, seconds);
    }

    public String getOriginalButtonText() {
        return originalButtonText;
    }

    public void shutdown() {
        stopPolling();
        if (notificationHandler != null) {
            notificationHandler.shutdown();
        }
    }
}