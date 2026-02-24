package com.autodeploy.ui.overlay;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Gestionează overlay-urile modale ale aplicației: blur de fundal,
 * loading indicator și dialoguri de reconectare.
 * <p>
 * Funcționare: aplică un {@link GaussianBlur} pe {@code contentPane} (conținutul principal)
 * și suprapune un {@link StackPane} semi-transparent cu conținut centrat.
 * Necesită ca root-ul ferestrei să fie un {@link StackPane} cu contentPane ca prim copil.
 * <p>
 * Overlay-uri disponibile:
 * <ul>
 *   <li>{@link #showSimpleBlur()} — blur fără conținut (ex: în timpul încărcării inițiale)</li>
 *   <li>{@link #showLoadingOverlay} — progress indicator + mesaj</li>
 *   <li>{@link #showReconnectOverlay} — dialog "Connection Lost" cu Reconnect/Close</li>
 *   <li>{@link #showReconnectFailure} — dialog de eroare cu Try Again/Close</li>
 * </ul>
 * <p>
 * <b>Maxim un overlay activ:</b> afișarea unui overlay nou îl elimină pe cel anterior.
 * Toate metodele sunt thread-safe — pot fi apelate din orice thread.
 */
public class UIOverlayManager {

    private static final double BLUR_RADIUS = 10.0;

    private static final String OVERLAY_STYLE =
            "-fx-background-color: rgba(0, 0, 0, 0.4);";
    private static final String OVERLAY_DARK_STYLE =
            "-fx-background-color: rgba(0, 0, 0, 0.5);";
    private static final String BOX_STYLE =
            "-fx-background-color: -color-bg-default; " +
                    "-fx-background-radius: 12px; " +
                    "-fx-padding: 40px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);";

    private static final String PRIMARY_BTN_STYLE =
            "-fx-background-color: -color-accent-emphasis; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-cursor: hand; " +
                    "-fx-background-radius: 6px;";
    private static final String SECONDARY_BTN_STYLE =
            "-fx-background-color: -color-bg-subtle; " +
                    "-fx-text-fill: -color-fg-default; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-cursor: hand; " +
                    "-fx-background-radius: 6px; " +
                    "-fx-border-color: -color-border-default; " +
                    "-fx-border-width: 1.5px;";

    private static final double BUTTON_WIDTH = 120;
    private static final double BUTTON_HEIGHT = 40;

    /**
     * Root-ul ferestrei (StackPane) — overlay-ul se adaugă ca copil peste contentPane.
     * Structura: rootPane → [contentPane, currentOverlay]
     */
    private final StackPane rootPane;
    /** Conținutul principal al ferestrei — primește efectul de blur. */
    private final VBox contentPane;
    /** Overlay-ul curent (sau null dacă nu e activ). Maxim unul la un moment dat. */
    private StackPane currentOverlay;

    public UIOverlayManager(StackPane rootPane, VBox contentPane) {
        this.rootPane = rootPane;
        this.contentPane = contentPane;
    }

    public void showSimpleBlur() {
        runOnFxThread(() -> showOverlay(OVERLAY_STYLE));
    }

    public void showLoadingOverlay(String title, String subtitle) {
        runOnFxThread(() -> {
            StackPane overlay = showOverlay(OVERLAY_STYLE);
            if (overlay != null) {
                overlay.getChildren().add(createLoadingBox(title, subtitle));
            }
        });
    }

    public void showReconnectOverlay(String serverName, String serverHost,
                                     Runnable onReconnect, Runnable onClose) {
        runOnFxThread(() -> {
            StackPane overlay = showOverlay(OVERLAY_DARK_STYLE);
            if (overlay != null) {
                Label serverLabel = createLabel(
                        serverName + " (" + serverHost + ")",
                        "-fx-font-size: 13px; -fx-text-fill: -color-fg-muted;");

                overlay.getChildren().add(createDialogBox(
                        "Connection Lost",
                        "The connection to the server was lost.",
                        "Reconnect", onReconnect,
                        onClose,
                        serverLabel
                ));
            }
        });
    }

    public void showReconnectFailure(String title, String errorMessage,
                                     Runnable onReconnect, Runnable onClose) {
        runOnFxThread(() -> {
            StackPane overlay = showOverlay(OVERLAY_DARK_STYLE);
            if (overlay != null) {
                overlay.getChildren().add(createDialogBox(
                        title,
                        errorMessage,
                        "Try Again", onReconnect,
                        onClose
                ));
            }
        });
    }

    public void hideOverlay() {
        runOnFxThread(() -> {
            if (contentPane == null || rootPane == null) return;

            contentPane.setEffect(null);

            if (currentOverlay != null) {
                rootPane.getChildren().remove(currentOverlay);
                currentOverlay = null;
            }
        });
    }

    /**
     * Creează overlay-ul de bază: elimină cel anterior (dacă există),
     * aplică blur pe conținut, și adaugă un StackPane semi-transparent.
     *
     * @return overlay-ul creat (pentru a adăuga conținut), sau null dacă pane-urile lipsesc
     */
    private StackPane showOverlay(String overlayStyle) {
        if (contentPane == null || rootPane == null) return null;

        if (currentOverlay != null) {
            rootPane.getChildren().remove(currentOverlay);
        }

        contentPane.setEffect(new GaussianBlur(BLUR_RADIUS));

        currentOverlay = new StackPane();
        currentOverlay.setStyle(overlayStyle);
        rootPane.getChildren().add(currentOverlay);

        return currentOverlay;
    }

    private VBox createLoadingBox(String title, String subtitle) {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(60, 60);

        return createCenteredBox(350, 200,
                progress,
                createLabel(title, "-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;"),
                createLabel(subtitle, "-fx-font-size: 14px; -fx-text-fill: -color-fg-muted;")
        );
    }

    /**
     * Creează un dialog box centrat cu: icon ⚠️, titlu, mesaj, noduri extra opționale,
     * și o bară de butoane (primary + Close).
     * Nodurile extra (ex: server info label) sunt inserate între mesaj și butoane.
     */
    private VBox createDialogBox(String title, String message,
                                 String primaryBtnText, Runnable onPrimary,
                                 Runnable onClose,
                                 Node... extraNodes) {
        Label warningIcon = new Label("⚠️");
        warningIcon.setStyle("-fx-font-size: 48px;");

        Label titleLabel = createLabel(title,
                "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #cf222e;");

        Label messageLabel = createLabel(message,
                "-fx-font-size: 14px; -fx-text-fill: -color-fg-default;");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(320);
        messageLabel.setAlignment(Pos.CENTER);

        HBox buttonBox = createButtonBar(primaryBtnText, onPrimary, onClose);

        VBox box = createCenteredBox(400, -1, warningIcon, titleLabel, messageLabel);

        for (Node node : extraNodes) {
            box.getChildren().add(node);
        }

        box.getChildren().add(buttonBox);
        return box;
    }

    private HBox createButtonBar(String primaryText, Runnable onPrimary, Runnable onClose) {
        MFXButton primaryBtn = createButton(primaryText, PRIMARY_BTN_STYLE);
        primaryBtn.setOnAction(e -> onPrimary.run());

        MFXButton closeBtn = createButton("Close", SECONDARY_BTN_STYLE);
        closeBtn.setOnAction(e -> onClose.run());

        HBox buttonBox = new HBox(15, primaryBtn, closeBtn);
        buttonBox.setAlignment(Pos.CENTER);
        return buttonBox;
    }

    private VBox createCenteredBox(double maxWidth, double maxHeight, Node... children) {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setStyle(BOX_STYLE);
        box.setMaxWidth(maxWidth);
        if (maxHeight > 0) box.setMaxHeight(maxHeight);
        box.getChildren().addAll(children);
        return box;
    }

    private Label createLabel(String text, String style) {
        Label label = new Label(text);
        label.setStyle(style);
        return label;
    }

    private MFXButton createButton(String text, String style) {
        MFXButton button = new MFXButton(text);
        button.setStyle(style);
        button.setPrefWidth(BUTTON_WIDTH);
        button.setPrefHeight(BUTTON_HEIGHT);
        return button;
    }

    /**
     * Asigură execuția pe JavaFX Application Thread.
     * Toate metodele publice trec prin asta — overlay-ul poate fi
     * declanșat din orice thread (ex: connection lost pe background thread).
     */
    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}