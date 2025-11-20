package com.autodeploy.config;

import com.autodeploy.model.Server;
import com.autodeploy.sftp.SftpManager;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class RestartManager {
    private final Server server;
    private final SftpManager sftpManager;
    private final String currentUsername;
    private final Gson gson = new Gson();

    private RestartStatus lastStatus;
    private Thread pollingThread;
    private volatile boolean polling = false;
    private final List<Consumer<RestartStatus>> listeners = new CopyOnWriteArrayList<>();

    public RestartManager(Server server, SftpManager sftpManager, String currentUsername) {
        this.server = server;
        this.sftpManager = sftpManager;
        this.currentUsername = currentUsername;
    }

    /**
     * Execute command via existing SFTP connection
     */
    private String executeCommand(String command) throws Exception {
        // Folosește metoda ta existentă din SftpManager
        return sftpManager.executeCommand(command);
    }

    /**
     * Get current restart status branch restartManager
     */
    public RestartStatus getStatus() throws Exception {
        String command = server.getRestartScript() + " " + currentUsername + " get";
        String output = executeCommand(command);
        return gson.fromJson(output, RestartStatus.class);
    }

    /**
     * Request server restart (will auto-execute after 30s if not rejected)
     */
    public RestartStatus requestRestart() throws Exception {
        String command = server.getRestartScript() + " " + currentUsername + " request";
        String output = executeCommand(command);

        if (output.startsWith("ERROR:")) {
            throw new RuntimeException(output.replace("ERROR:", ""));
        }

        return gson.fromJson(output, RestartStatus.class);
    }

    /**
     * Reject pending restart (no reason required)
     */
    public RestartStatus rejectRestart() throws Exception {
        String command = server.getRestartScript() + " " + currentUsername + " reject \"Rejected by user\"";
        String output = executeCommand(command);

        if (output.startsWith("ERROR:")) {
            throw new RuntimeException(output.replace("ERROR:", ""));
        }

        return gson.fromJson(output, RestartStatus.class);
    }

    /**
     * Cancel restart request (only requester can cancel)
     */
    public RestartStatus cancelRestart() throws Exception {
        String command = server.getRestartScript() + " " + currentUsername + " cancel";
        String output = executeCommand(command);

        if (output.startsWith("ERROR:")) {
            throw new RuntimeException(output.replace("ERROR:", ""));
        }

        return gson.fromJson(output, RestartStatus.class);
    }

    /**
     * Start polling for status changes
     */
    public void startPolling(int intervalMs) {
        if (polling) {
            return;
        }

        polling = true;
        pollingThread = new Thread(() -> {
            while (polling) {
                try {
                    RestartStatus newStatus = getStatus();

                    if (lastStatus == null || newStatus.getVersion() != lastStatus.getVersion()) {
                        final RestartStatus statusToNotify = newStatus;
                        Platform.runLater(() -> notifyListeners(statusToNotify));
                        lastStatus = newStatus;
                    }

                    Thread.sleep(intervalMs);

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Polling error: " + e.getMessage());
                }
            }
        }, "RestartManager-Polling");

        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    /**
     * Stop polling
     */
    public void stopPolling() {
        polling = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            pollingThread = null;
        }
    }

    /**
     * Add status change listener
     */
    public void addListener(Consumer<RestartStatus> listener) {
        listeners.add(listener);
    }

    /**
     * Remove listener
     */
    public void removeListener(Consumer<RestartStatus> listener) {
        listeners.remove(listener);
    }

    /**
     * Notify all listeners
     */
    private void notifyListeners(RestartStatus status) {
        for (Consumer<RestartStatus> listener : listeners) {
            try {
                listener.accept(status);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Status data class
     */
    public static class RestartStatus {
        private long version;

        @SerializedName("in_progress")
        private boolean inProgress;

        private String requester;

        @SerializedName("requested_at")
        private Long requestedAt;

        @SerializedName("wait_until")
        private Long waitUntil;

        private String status;

        private List<Rejection> rejections = new ArrayList<>();

        @SerializedName("last_update")
        private long lastUpdate;

        public long getVersion() { return version; }
        public boolean isInProgress() { return inProgress; }
        public String getRequester() { return requester; }
        public Long getRequestedAt() { return requestedAt; }
        public Long getWaitUntil() { return waitUntil; }
        public String getStatus() { return status; }
        public List<Rejection> getRejections() { return rejections; }
        public long getLastUpdate() { return lastUpdate; }

        public int getTimeRemaining() {
            if (waitUntil == null) return 0;
            return (int) Math.max(0, waitUntil - (System.currentTimeMillis() / 1000));
        }

        public boolean isIdle() { return "idle".equals(status); }
        public boolean isPending() { return "pending".equals(status); }
        public boolean isApproved() { return "approved".equals(status); }
        public boolean isRejected() { return "rejected".equals(status); }
        public boolean isExecuting() { return "executing".equals(status); }

        public static class Rejection {
            private String user;
            private long timestamp;
            private String reason;

            public String getUser() { return user; }
            public long getTimestamp() { return timestamp; }
            public String getReason() { return reason; }
        }
    }
}