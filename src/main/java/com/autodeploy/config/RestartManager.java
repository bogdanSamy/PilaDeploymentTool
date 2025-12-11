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

    private String executeCommand(String command) throws Exception {
        return sftpManager.executeCommand(command);
    }

    public RestartStatus getStatus() throws Exception {
        String command = server.getRestartManagerScript() + " " + currentUsername + " get";

        String output = executeCommand(command);

        if (output == null || output.trim().isEmpty()) {
            return null;
        }

        output = output.trim();

        if (output.startsWith("ERROR:")) {
            System.err.println("ERROR from script: " + output);
            return null;
        }

        if (!output.startsWith("{")) {
            return null;
        }

        try {
            return gson.fromJson(output, RestartStatus.class);
        } catch (Exception e) {
            System.err.println("ERROR: Failed to parse JSON: " + e.getMessage());
            return null;
        }
    }

    public RestartStatus requestRestart(String projectName) throws Exception {
        // Sanitize project name to prevent shell injection issues (basic quote handling)
        String safeProjectName = (projectName != null) ? projectName.replace("\"", "\\\"") : "";

        // Construct command: ./script.sh username request "My Project"
        String command = String.format("%s %s request \"%s\"",
                server.getRestartManagerScript(),
                currentUsername,
                safeProjectName);

        System.out.println("---------------------------------------");
        System.out.println("DEBUG: Executing request command");
        System.out.println("Command: " + command);

        String output = executeCommand(command);

        System.out.println("Raw request output: [" + output + "]");

        // Handle empty response (fire-and-forget might return empty if successful)
        if (output == null || output.trim().isEmpty()) {
            // If empty, we assume it started. Fetch status to confirm.
            System.out.println("Info: Empty response, fetching status manually...");
            Thread.sleep(200); // Allow file system to update
            return getStatus();
        }

        output = output.trim();

        if (output.startsWith("ERROR:")) {
            throw new RuntimeException(output.replace("ERROR:", ""));
        }

        // The new script usually returns "OK:..." string.
        // If so, we just fetch the status JSON manually.
        if (output.startsWith("OK:")) {
            System.out.println("✓ " + output);
            Thread.sleep(200);
            return getStatus();
        }

        // If it returns JSON directly
        if (output.startsWith("{")) {
            try {
                return gson.fromJson(output, RestartStatus.class);
            } catch (Exception e) {
                // Fallback
                return getStatus();
            }
        }

        // Default fallback
        return getStatus();
    }

    /**
     * Reject pending restart
     */
    public RestartStatus rejectRestart() throws Exception {
        String command = server.getRestartManagerScript() + " " + currentUsername + " reject";

        System.out.println("DEBUG: Executing reject command: " + command);
        String output = executeCommand(command);

        if (output != null && output.startsWith("ERROR:")) {
            throw new RuntimeException(output.replace("ERROR:", ""));
        }

        System.out.println("✓ Reject executed. Refreshing status...");
        Thread.sleep(200);
        return getStatus();
    }

    public void startPolling(int intervalMs) {
        if (polling) return;

        polling = true;
        pollingThread = new Thread(() -> {
            while (polling) {
                try {
                    RestartStatus newStatus = getStatus();

                    if (newStatus != null) {
                        // Notify if status changed or first run
                        if (lastStatus == null || (newStatus.getVersion() != lastStatus.getVersion())) {
                            final RestartStatus statusToNotify = newStatus;
                            Platform.runLater(() -> notifyListeners(statusToNotify));
                            lastStatus = newStatus;
                        }
                    }
                    Thread.sleep(intervalMs);

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    try { Thread.sleep(intervalMs); } catch (InterruptedException ie) { break; }
                }
            }
        }, "RestartManager-Polling");

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

    public void addListener(Consumer<RestartStatus> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<RestartStatus> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(RestartStatus status) {
        for (Consumer<RestartStatus> listener : listeners) {
            try { listener.accept(status); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public static class RestartStatus {
        private long version;

        @SerializedName("in_progress")
        private boolean inProgress;

        private String requester;

        private String project;

        @SerializedName("requested_at")
        private Long requestedAt;

        @SerializedName("wait_until")
        private Long waitUntil;

        private String status;

        private List<Rejection> rejections = new ArrayList<>();

        @SerializedName("last_update")
        private long lastUpdate;

        // Getters
        public long getVersion() { return version; }
        public boolean isInProgress() { return inProgress; }
        public String getRequester() { return requester; }
        public String getProject() { return project; } // New Getter
        public Long getRequestedAt() { return requestedAt; }
        public Long getWaitUntil() { return waitUntil; }
        public String getStatus() { return status; }
        public List<Rejection> getRejections() { return rejections; }

        public long getLastUpdate() { return lastUpdate; }

        // Helpers
        public int getTimeRemaining() {
            if (waitUntil == null) return 0;
            return (int) Math.max(0, waitUntil - (System.currentTimeMillis() / 1000));
        }

        public boolean isIdle() { return "idle".equalsIgnoreCase(status); }
        public boolean isPending() { return "pending".equalsIgnoreCase(status); }
        public boolean isApproved() { return "approved".equalsIgnoreCase(status); }
        public boolean isRejected() { return "rejected".equalsIgnoreCase(status); }
        public boolean isExecuting() { return "executing".equalsIgnoreCase(status); }
        public boolean isCompleted() {return "completed".equalsIgnoreCase(status); }

        public static class Rejection {
            private String user;
            private long timestamp;
            public String getUser() { return user; }
            public long getTimestamp() { return timestamp; }
        }
    }
}