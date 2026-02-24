package com.autodeploy.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Model pentru statusul restart-ului de pe server.
 * Deserializat din JSON primit de la restart_manager.sh.
 * <p>
 * Statusuri posibile ale unei cereri:
 * <ul>
 *   <li><b>idle</b> — nicio cerere activă</li>
 *   <li><b>pending</b> — cerere în așteptare (countdown activ)</li>
 *   <li><b>approved</b> — cerere aprobată, urmează execuție</li>
 *   <li><b>executing</b> — restart în curs</li>
 *   <li><b>completed</b> — restart finalizat</li>
 *   <li><b>rejected</b> — cerere respinsă de un alt utilizator</li>
 * </ul>
 * <p>
 * <b>Distincție importantă:</b> {@code status} reflectă starea <i>cererii curente</i>,
 * în timp ce {@code activeRestart} indică dacă un restart fizic rulează pe server.
 * Cele două sunt independente — o cerere poate fi "rejected" dar serverul
 * să fie încă în restart de la o cerere anterioară.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestartStatus {

    private long version;

    @JsonProperty("in_progress")
    private boolean inProgress;

    private String requester;
    private String project;

    @JsonProperty("requested_at")
    private Long requestedAt;

    @JsonProperty("wait_until")
    private Long waitUntil;

    private String status;

    private List<Rejection> rejections = new ArrayList<>();

    @JsonProperty("active_restart")
    private ActiveRestart activeRestart;

    @JsonProperty("last_update")
    private long lastUpdate;

    // --- Getters ---

    public long getVersion() { return version; }
    public boolean isInProgress() { return inProgress; }
    public String getRequester() { return requester; }
    public String getProject() { return project; }
    public Long getRequestedAt() { return requestedAt; }
    public Long getWaitUntil() { return waitUntil; }
    public String getStatus() { return status; }
    public List<Rejection> getRejections() { return rejections; }
    public ActiveRestart getActiveRestart() { return activeRestart; }
    public long getLastUpdate() { return lastUpdate; }

    // --- Status checks ---

    public boolean isIdle() { return "idle".equalsIgnoreCase(status); }
    public boolean isPending() { return "pending".equalsIgnoreCase(status); }
    public boolean isApproved() { return "approved".equalsIgnoreCase(status); }
    public boolean isRejected() { return "rejected".equalsIgnoreCase(status); }
    public boolean isExecuting() { return "executing".equalsIgnoreCase(status); }
    public boolean isCompleted() { return "completed".equalsIgnoreCase(status); }

    /** Cererea e într-un stadiu activ (nu poate fi suprascrisă). */
    public boolean isBusy() {
        return isPending() || isExecuting() || isApproved();
    }

    /**
     * Există un restart real care rulează în background.
     * Independent de statusul cererii curente (pending/rejected/etc).
     */
    public boolean hasActiveRestart() {
        return activeRestart != null
                && activeRestart.getStartedAt() != null
                && activeRestart.getStartedAt() > 0;
    }

    /**
     * Cererea e rejected DAR un restart anterior încă rulează.
     * UI-ul trebuie să arate că serverul e încă indisponibil.
     */
    public boolean isRejectedButStillRestarting() {
        return isRejected() && hasActiveRestart();
    }

    /**
     * Cerere pending în timp ce un restart anterior încă rulează.
     * UI-ul trebuie să arate ambele stări simultan.
     */
    public boolean isPendingOverActiveRestart() {
        return isPending() && hasActiveRestart();
    }

    /** Serverul este efectiv în restart (fie executing, fie active_restart există). */
    public boolean isServerRestarting() {
        return isExecuting() || hasActiveRestart();
    }

    /** Secunde rămase din countdown-ul de așteptare (pending → approved). */
    public int getTimeRemaining() {
        if (waitUntil == null) return 0;
        return (int) Math.max(0, waitUntil - nowEpochSeconds());
    }

    /**
     * Secunde scurse de la începutul restartului activ.
     * Preferă active_restart.started_at, fallback pe requested_at.
     */
    public long getActiveRestartElapsedSeconds() {
        if (hasActiveRestart()) {
            return Math.max(0, nowEpochSeconds() - activeRestart.getStartedAt());
        }
        if (requestedAt != null && requestedAt > 0) {
            return Math.max(0, nowEpochSeconds() - requestedAt);
        }
        return 0;
    }

    /**
     * Detectează dacă statusul s-a schimbat față de un snapshot anterior.
     * Folosit de polling loop pentru a notifica UI-ul doar la schimbări reale.
     */
    public boolean hasChangedFrom(RestartStatus other) {
        if (other == null) return true;
        return this.lastUpdate != other.lastUpdate
                || !safeEquals(this.status, other.status)
                || this.hasActiveRestart() != other.hasActiveRestart();
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static long nowEpochSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    // --- Inner classes ---

    /**
     * Restart care rulează efectiv pe server.
     * Persistă independent de statusul cererii curente.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActiveRestart {
        private String requester;
        private String project;

        @JsonProperty("started_at")
        private Long startedAt;

        @JsonProperty("requested_at")
        private Long requestedAt;

        public String getRequester() { return requester; }
        public String getProject() { return project; }
        public Long getStartedAt() { return startedAt; }
        public Long getRequestedAt() { return requestedAt; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rejection {
        private String user;
        private long timestamp;

        public String getUser() { return user; }
        public long getTimestamp() { return timestamp; }
    }
}