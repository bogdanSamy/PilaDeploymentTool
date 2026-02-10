package com.autodeploy.ui.overlay;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Service for managing UI overlays (blur effects, loading indicators, etc.)
 */
public class UIOverlayManager {

    private static final double BLUR_RADIUS = 10.0;
    private static final String OVERLAY_STYLE = "-fx-background-color: rgba(0, 0, 0, 0.4);";
    private static final String OVERLAY_DARK_STYLE = "-fx-background-color: rgba(0, 0, 0, 0.5);";

    private static final String BOX_STYLE =
            "-fx-background-color: -color-bg-default; " +
                    "-fx-background-radius: 12px; " +
                    "-fx-padding: 40px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);";

    private final StackPane rootPane;
    private final VBox contentPane;

    private StackPane currentOverlay;

    public UIOverlayManager(StackPane rootPane, VBox contentPane) {
        this.rootPane = rootPane;
        this.contentPane = contentPane;
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Show simple blur overlay (no content)
     */
    public void showSimpleBlur() {
        runOnFxThread(() -> {
            // Guard against null panes (can happen during early initialize timing)
            if (contentPane == null || rootPane == null) {
                return;
            }

            // Don't hide overlay if we are just replacing it or stacking it
            // But for simple blur, we usually want to clear previous content
            if (currentOverlay != null) {
                rootPane.getChildren().remove(currentOverlay);
            }

            GaussianBlur blur = new GaussianBlur(BLUR_RADIUS);
            contentPane.setEffect(blur);

            currentOverlay = new StackPane();
            currentOverlay.setStyle(OVERLAY_STYLE);

            rootPane.getChildren().add(currentOverlay);
        });
    }

    /**
     * Show loading overlay with progress indicator
     */
    public void showLoadingOverlay(String title, String subtitle) {
        runOnFxThread(() -> {
            // Guard against null panes
            if (contentPane == null || rootPane == null) {
                return;
            }

            if (currentOverlay != null) {
                rootPane.getChildren().remove(currentOverlay);
                currentOverlay = null;
            }

            GaussianBlur blur = new GaussianBlur(BLUR_RADIUS);
            contentPane.setEffect(blur);

            currentOverlay = new StackPane();
            currentOverlay.setStyle(OVERLAY_STYLE);

            VBox loadingBox = createLoadingBox(title, subtitle);
            currentOverlay.getChildren().add(loadingBox);

            rootPane.getChildren().add(currentOverlay);
        });
    }

    /**
     * Show reconnect overlay with action buttons
     */
    public void showReconnectOverlay(String serverName, String serverHost,
                                     Runnable onReconnect, Runnable onClose) {
        runOnFxThread(() -> {
            if (contentPane == null || rootPane == null) {
                return;
            }

            if (currentOverlay != null) {
                rootPane.getChildren().remove(currentOverlay);
                currentOverlay = null;
            }

            GaussianBlur blur = new GaussianBlur(BLUR_RADIUS);
            contentPane.setEffect(blur);

            currentOverlay = new StackPane();
            currentOverlay.setStyle(OVERLAY_DARK_STYLE);

            VBox reconnectBox = createReconnectBox(serverName, serverHost, onReconnect, onClose);
            currentOverlay.getChildren().add(reconnectBox);

            rootPane.getChildren().add(currentOverlay);
        });
    }

    /**
     * Show reconnect overlay with a specific error message
     */
    public void showReconnectFailure(String title, String errorMessage,
                                     Runnable onReconnect, Runnable onClose) {
        runOnFxThread(() -> {
            if (contentPane == null || rootPane == null) return;

            if (currentOverlay != null) {
                rootPane.getChildren().remove(currentOverlay);
                currentOverlay = null;
            }

            if (contentPane.getEffect() == null) {
                GaussianBlur blur = new GaussianBlur(BLUR_RADIUS);
                contentPane.setEffect(blur);
            }

            currentOverlay = new StackPane();
            currentOverlay.setStyle(OVERLAY_DARK_STYLE);

            VBox reconnectBox = createReconnectBoxWithError(title, errorMessage, onReconnect, onClose);
            currentOverlay.getChildren().add(reconnectBox);

            rootPane.getChildren().add(currentOverlay);
        });
    }

    private VBox createReconnectBoxWithError(String title, String errorMessage,
                                             Runnable onReconnect, Runnable onClose) {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setStyle(BOX_STYLE);
        box.setMaxWidth(400);

        Label warningIcon = new Label("⚠️");
        warningIcon.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #cf222e;");

        Label errorLabel = new Label(errorMessage);
        errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-default;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(320);
        errorLabel.setAlignment(Pos.CENTER);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        MFXButton reconnectBtn = new MFXButton("Try Again");
        reconnectBtn.setStyle("-fx-background-color: -color-accent-emphasis; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-cursor: hand; " +
                "-fx-background-radius: 6px;");
        reconnectBtn.setPrefWidth(120);
        reconnectBtn.setPrefHeight(40);
        reconnectBtn.setOnAction(e -> onReconnect.run());

        MFXButton closeBtn = new MFXButton("Close");
        closeBtn.setStyle("-fx-background-color: -color-bg-subtle; " +
                "-fx-text-fill: -color-fg-default; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-cursor: hand; " +
                "-fx-background-radius: 6px; " +
                "-fx-border-color: -color-border-default; " +
                "-fx-border-width: 1.5px;");
        closeBtn.setPrefWidth(120);
        closeBtn.setPrefHeight(40);
        closeBtn.setOnAction(e -> onClose.run());

        buttonBox.getChildren().addAll(reconnectBtn, closeBtn);

        box.getChildren().addAll(warningIcon, titleLabel, errorLabel, buttonBox);
        return box;
    }

    /**
     * Hide overlay and remove blur effect
     */
    public void hideOverlay() {
        runOnFxThread(() -> {
            if (contentPane == null || rootPane == null) {
                return;
            }

            contentPane.setEffect(null);

            if (currentOverlay != null) {
                rootPane.getChildren().remove(currentOverlay);
                currentOverlay = null;
            }
        });
    }

    private VBox createLoadingBox(String title, String subtitle) {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setStyle(BOX_STYLE);
        box.setMaxWidth(350);
        box.setMaxHeight(200);

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(60, 60);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-muted;");

        box.getChildren().addAll(progressIndicator, titleLabel, subtitleLabel);

        return box;
    }

    private VBox createReconnectBox(String serverName, String serverHost,
                                    Runnable onReconnect, Runnable onClose) {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        box.setStyle(BOX_STYLE);
        box.setMaxWidth(400);

        Label warningIcon = new Label("⚠️");
        warningIcon.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label("Connection Lost");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #cf222e;");

        Label messageLabel = new Label("The connection to the server was lost.");
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-default;");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(320);
        messageLabel.setAlignment(Pos.CENTER);

        Label serverLabel = new Label(serverName + " (" + serverHost + ")");
        serverLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-fg-muted;");

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        MFXButton reconnectBtn = new MFXButton("Reconnect");
        reconnectBtn.setStyle("-fx-background-color: -color-accent-emphasis; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-cursor: hand; " +
                "-fx-background-radius: 6px;");
        reconnectBtn.setPrefWidth(120);
        reconnectBtn.setPrefHeight(40);
        reconnectBtn.setOnAction(e -> onReconnect.run());

        MFXButton closeBtn = new MFXButton("Close");
        closeBtn.setStyle("-fx-background-color: -color-bg-subtle; " +
                "-fx-text-fill: -color-fg-default; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-cursor: hand; " +
                "-fx-background-radius: 6px; " +
                "-fx-border-color: -color-border-default; " +
                "-fx-border-width: 1.5px;");
        closeBtn.setPrefWidth(120);
        closeBtn.setPrefHeight(40);
        closeBtn.setOnAction(e -> onClose.run());

        buttonBox.getChildren().addAll(reconnectBtn, closeBtn);

        box.getChildren().addAll(warningIcon, titleLabel, messageLabel, serverLabel, buttonBox);

        return box;
    }

    public boolean isOverlayShowing() {
        return currentOverlay != null;
    }
}