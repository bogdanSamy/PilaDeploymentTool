package com.autodeploy.ui.window.component;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Gestionează panoul de log din fereastra de deployment.
 * <p>
 * Funcționalitate:
 * <ul>
 *   <li>Formatare mesaje cu timestamp {@code [HH:mm:ss]}</li>
 *   <li>Toggle vizibilitate (Show/Hide Logs)</li>
 *   <li>Monitorizare pasivă: detectează erori de conexiune în mesajele
 *       de log și notifică prin callback — acționează ca un "safety net"
 *       suplimentar pe lângă monitoring-ul activ din SftpManager</li>
 * </ul>
 */
public class LogPanelManager {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Patterns detectate în mesajele de log care indică pierderea conexiunii. */
    private static final String[] CONNECTION_ERROR_PATTERNS = {
            "session is down", "ssh session not connected", "connection lost"
    };

    private final TextArea logArea;
    private final VBox logSection;
    private final Button toggleLogBtn;

    private Consumer<String> connectionErrorCallback;
    private boolean logVisible = false;

    public LogPanelManager(TextArea logArea, VBox logSection, Button toggleLogBtn) {
        this.logArea = logArea;
        this.logSection = logSection;
        this.toggleLogBtn = toggleLogBtn;
    }

    public void setup() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        toggleLogBtn.setOnAction(e -> toggleVisibility());
    }

    public void setConnectionErrorCallback(Consumer<String> callback) {
        this.connectionErrorCallback = callback;
    }

    public void log(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            monitorForErrors(message);
        });
    }

    private void toggleVisibility() {
        logVisible = !logVisible;
        logSection.setVisible(logVisible);
        logSection.setManaged(logVisible);
        toggleLogBtn.setText(logVisible ? "📋 Hide Logs" : "📋 Show Logs");
        if (logVisible) log("✓ Log panel opened");
    }

    /**
     * Scanează fiecare mesaj de log pentru patterns de eroare de conexiune.
     * Dacă detectează unul, notifică callback-ul — care de obicei declanșează
     * {@code connectionHandler.notifyConnectionLost()}.
     * Funcționează ca o plasă de siguranță: dacă SftpManager-ul nu detectează
     * pierderea conexiunii, log-urile de la operații eșuate o vor detecta.
     */
    private void monitorForErrors(String message) {
        if (connectionErrorCallback == null) return;

        String lowerMsg = message.toLowerCase();
        for (String pattern : CONNECTION_ERROR_PATTERNS) {
            if (lowerMsg.contains(pattern)) {
                connectionErrorCallback.accept(message);
                return;
            }
        }
    }
}