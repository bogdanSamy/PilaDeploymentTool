package com.autodeploy.ui.window.component;

import com.autodeploy.domain.model.RestartStatus;
import com.autodeploy.service.restart.RestartService;
import com.autodeploy.ui.dialog.CustomAlert;
import com.autodeploy.ui.overlay.UIOverlayManager;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.function.Consumer;

import static com.autodeploy.core.constants.Constants.TIMER_UPDATE_INTERVAL_MS;

/**
 * Gestionează butonul de restart și timer-ul vizual din fereastra de deployment.
 * <p>
 * <b>Regula centrală a timer-ului:</b>
 * <ul>
 *   <li>Timeline-ul rulează DOAR când {@code active_restart} există pe server</li>
 *   <li>Elapsed-ul vine de la {@code active_restart.started_at} (server timestamp, nu local)</li>
 *   <li>Când cererea e rejected DAR {@code active_restart} există → timer-ul CONTINUĂ
 *       (restartul fizic e independent de statusul cererii)</li>
 *   <li>Timer-ul se resetează DOAR la un restart NOU (alt started_at/requestedAt)</li>
 *   <li>Timer-ul se oprește DOAR când {@code active_restart} dispare (completed/idle)</li>
 * </ul>
 * <p>
 * Butonul reflectă starea compusă (status cerere + active_restart) prin:
 * <ul>
 *   <li><b>Text:</b> "⏳ Pending + 🔄 (01:23)", "🔄 Restarting (01:23)", "❌ Rejected (🔄 01:23)"</li>
 *   <li><b>Stil (border):</b> warning=pending, danger=executing/active, mix=pending over active</li>
 * </ul>
 */
public class RestartHandler {

    private static final String DEFAULT_BUTTON_TEXT = "Restart Server";

    private static final String STYLE_IDLE = "";
    private static final String STYLE_PENDING =
            "-fx-opacity: 0.85; " +
                    "-fx-border-color: -color-warning-emphasis; " +
                    "-fx-border-width: 1.5px; " +
                    "-fx-border-radius: 4px;";
    private static final String STYLE_EXECUTING =
            "-fx-opacity: 0.85; " +
                    "-fx-border-color: -color-danger-emphasis; " +
                    "-fx-border-width: 1.5px; " +
                    "-fx-border-radius: 4px;";
    /** Pending + un restart anterior rulează în background. */
    private static final String STYLE_PENDING_OVER_ACTIVE =
            "-fx-opacity: 0.85; " +
                    "-fx-border-color: -color-warning-emphasis; " +
                    "-fx-border-width: 1.5px; " +
                    "-fx-border-radius: 4px; " +
                    "-fx-background-color: derive(-color-danger-emphasis, 80%);";
    /** Cererea e rejected, dar restartul fizic continuă. */
    private static final String STYLE_REJECTED_BUT_ACTIVE =
            "-fx-opacity: 0.85; " +
                    "-fx-border-color: -color-danger-emphasis; " +
                    "-fx-border-width: 1.5px; " +
                    "-fx-border-radius: 4px;";

    private final RestartService restartService;
    private final MFXButton restartServerBtn;
    private final UIOverlayManager overlayManager;
    private final Window ownerWindow;
    private final Consumer<String> logger;
    private final String serverDisplayName;
    private final String projectName;

    private Timeline restartTimerTimeline;
    private volatile String currentState = "idle";
    private volatile boolean hasActiveRestart = false;

    /**
     * Tracking-ul ultimului restart activ — pentru a detecta când
     * un restart NOU începe (alt started_at/requestedAt) vs. același restart
     * care continuă să fie raportat de polling.
     */
    private Long lastActiveRestartRequestedAt = null;
    private Long lastActiveRestartStartedAt = null;

    public RestartHandler(RestartService restartService, MFXButton restartServerBtn,
                          UIOverlayManager overlayManager, Window ownerWindow,
                          Consumer<String> logger, String serverDisplayName,
                          String projectName) {
        this.restartService = restartService;
        this.restartServerBtn = restartServerBtn;
        this.overlayManager = overlayManager;
        this.ownerWindow = ownerWindow;
        this.logger = logger;
        this.serverDisplayName = serverDisplayName;
        this.projectName = projectName;
    }

    public void setupCallbacks() {
        restartService.addStatusListener(status -> Platform.runLater(() -> {
            if (status == null || status.getStatus() == null) return;

            currentState = status.getStatus().toLowerCase().trim();
            hasActiveRestart = status.hasActiveRestart();

            updateButtonStyle();
            restartServerBtn.setDisable("pending".equals(currentState));

            if (hasActiveRestart) {
                handleActiveTimerPersistence(status);
            } else {
                handleNoActiveRestartFlow();
            }
        }));
    }

    /**
     * active_restart EXISTĂ → timer-ul trebuie să ruleze.
     * Trei cazuri:
     * <ol>
     *   <li>Nu avem timeline → pornim unul (primul detect)</li>
     *   <li>started_at/requestedAt s-a schimbat → restart NOU, reset timer</li>
     *   <li>Același restart → timeline-ul ticăie deja, doar update text</li>
     * </ol>
     */
    private void handleActiveTimerPersistence(RestartStatus status) {
        Long currentStartedAt = status.getActiveRestart() != null
                ? status.getActiveRestart().getStartedAt() : null;
        Long currentRequestedAt = status.getActiveRestart() != null
                ? status.getActiveRestart().getRequestedAt() : null;

        boolean isSameRestart = lastActiveRestartStartedAt != null
                && lastActiveRestartStartedAt.equals(currentStartedAt)
                && lastActiveRestartRequestedAt != null
                && lastActiveRestartRequestedAt.equals(currentRequestedAt);

        if (restartTimerTimeline == null) {
            lastActiveRestartStartedAt = currentStartedAt;
            lastActiveRestartRequestedAt = currentRequestedAt;
            createAndStartTimeline();
        } else if (!isSameRestart && currentStartedAt != null) {
            stopTimerAnimation();
            lastActiveRestartStartedAt = currentStartedAt;
            lastActiveRestartRequestedAt = currentRequestedAt;
            createAndStartTimeline();
        }

        updateButtonTextBasedOnState();
    }

    private void createAndStartTimeline() {
        restartTimerTimeline = new Timeline(new KeyFrame(
                Duration.millis(TIMER_UPDATE_INTERVAL_MS),
                event -> updateButtonTextBasedOnState()
        ));
        restartTimerTimeline.setCycleCount(Timeline.INDEFINITE);
        restartTimerTimeline.play();
    }

    /**
     * Textul butonului combină starea cererii cu elapsed-ul din active_restart.
     * Elapsed-ul vine de la server (via RestartService), nu de la un timer local.
     */
    private void updateButtonTextBasedOnState() {
        String elapsed = restartService.getFormattedElapsedTime();

        switch (currentState) {
            case "pending" -> restartServerBtn.setText("⏳ Pending + 🔄 " + elapsed);
            case "rejected" -> restartServerBtn.setText("❌ Rejected (🔄 " + elapsed + ")");
            default -> restartServerBtn.setText("🔄 Restarting " + elapsed);
        }
    }

    /**
     * active_restart NU EXISTĂ → serverul nu mai restartează.
     * Pending simplu = doar text, fără timer.
     * Completed = text temporar "✅ Restart Done" timp de 5s, apoi reset.
     * Idle/rejected fără active = reset total.
     */
    private void handleNoActiveRestartFlow() {
        lastActiveRestartStartedAt = null;
        lastActiveRestartRequestedAt = null;

        if ("pending".equals(currentState)) {
            stopTimerAnimation();
            restartServerBtn.setText("⏳ Pending...");
        } else if ("completed".equals(currentState)) {
            showCompletedState();
        } else {
            resetToIdle();
        }
    }

    private void updateButtonStyle() {
        if ("pending".equals(currentState) && hasActiveRestart) {
            restartServerBtn.setStyle(STYLE_PENDING_OVER_ACTIVE);
        } else if ("pending".equals(currentState)) {
            restartServerBtn.setStyle(STYLE_PENDING);
        } else if ("rejected".equals(currentState) && hasActiveRestart) {
            restartServerBtn.setStyle(STYLE_REJECTED_BUT_ACTIVE);
        } else if ("executing".equals(currentState) || hasActiveRestart) {
            restartServerBtn.setStyle(STYLE_EXECUTING);
        } else {
            restartServerBtn.setStyle(STYLE_IDLE);
        }
    }

    /**
     * Afișează "✅ Restart Done" timp de 5s, apoi revine la textul default.
     * Folosește un thread daemon separat pentru delay.
     */
    private void showCompletedState() {
        stopTimerAnimation();
        restartServerBtn.setStyle(STYLE_IDLE);
        restartServerBtn.setText("✅ Restart Done");

        AsyncHelper.runDaemon(() -> {
            try {
                Thread.sleep(5000);
                Platform.runLater(() -> {
                    if ("completed".equals(currentState) || "idle".equals(currentState)) {
                        restartServerBtn.setText(DEFAULT_BUTTON_TEXT);
                    }
                });
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Restart-Done-Reset");
    }

    private void resetToIdle() {
        stopTimerAnimation();
        restartServerBtn.setStyle(STYLE_IDLE);
        restartServerBtn.setText(DEFAULT_BUTTON_TEXT);
    }

    private void stopTimerAnimation() {
        if (restartTimerTimeline != null) {
            restartTimerTimeline.stop();
            restartTimerTimeline = null;
        }
    }

    /**
     * Gestionează click-ul pe butonul de restart.
     * Mesajul de confirmare se adaptează la starea curentă:
     * <ul>
     *   <li>Idle → confirmare simplă</li>
     *   <li>Pending (fără active) → override, timer-ul de 30s se resetează</li>
     *   <li>Pending + active → override, restartul curent continuă în background</li>
     *   <li>Executing / active → request nou, restartul curent continuă</li>
     * </ul>
     */
    public void handleRestart() {
        logger.accept("🔄 Restart server requested...");
        overlayManager.showSimpleBlur();

        String title;
        String message;

        if ("pending".equals(currentState) && !hasActiveRestart) {
            title = "Override pending restart?";
            message = "A restart is already pending.\n\n" +
                    "This will replace it with a NEW request.\n" +
                    "The 30s approval timer will reset.";
        } else if ("pending".equals(currentState) && hasActiveRestart) {
            title = "Override pending restart?";
            message = "A restart is already pending AND the server is currently restarting.\n\n" +
                    "This will replace the pending request.\n" +
                    "The current restart continues in the background.";
        } else if ("executing".equals(currentState) || hasActiveRestart) {
            title = "Request new restart?";
            message = "The server is currently restarting.\n\n" +
                    "A NEW 30s approval window will start.\n" +
                    "The current restart continues in the background.\n\n" +
                    "• If accepted: a new restart will execute after this one.\n" +
                    "• If rejected: the current restart continues unaffected.";
        } else {
            title = "Restart " + serverDisplayName + "?";
            message = "Requesting will notify all connected users.\n\n" +
                    "Auto-executes in 30s unless rejected.";
        }

        boolean confirmed = CustomAlert.showConfirmation(ownerWindow, title, message);
        overlayManager.hideOverlay();

        if (confirmed) {
            logger.accept("✓ User confirmed restart request");
            executeRequest();
        } else {
            logger.accept("⚠ Restart request cancelled by user");
        }
    }

    public void shutdown() {
        stopTimerAnimation();
        restartService.shutdown();
    }

    private void executeRequest() {
        var restartTask = restartService.requestRestartAsync(projectName);

        restartTask.setOnSucceeded(event -> {
            // Feedback instant — butonul se dezactivează imediat
            // Starea reală vine de la polling în max 2s
            restartServerBtn.setDisable(true);
        });

        restartTask.setOnFailed(event -> {
            Throwable error = restartTask.getException();
            logger.accept("✗ Failed to send restart request: " + error.getMessage());
            CustomAlert.showError("Restart Request Failed", error.getMessage());
        });

        AsyncHelper.runDaemon(restartTask, "Restart-Request");
    }
}