package com.autodeploy.ui.dialogs;

import com.autodeploy.assets.Assets;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Window;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class CustomAlert extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML private Button closeBtn;
    @FXML private SVGPath iconPath;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button okBtn;
    @FXML private Button cancelBtn;

    private static final int TITLE_BAR_HEIGHT = 30;

    public enum AlertType {
        ERROR, INFO, WARNING, CONFIRMATION
    }

    private final AlertType alertType;
    private final String title;
    private final String message;
    private boolean confirmed = false;

    /**
     * Constructs a new CustomAlert (application-modal)
     *
     * @param alertType The type of alert
     * @param title The alert title
     * @param message The alert message
     */
    public CustomAlert(AlertType alertType, String title, String message) {
        super(true); // Hide from taskbar
        this.alertType = alertType;
        this.title = title;
        this.message = message;

        try {
            Parent parent = Assets.load("/fxml/custom-alert.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            // Ensure dialogs are modal by default when no explicit owner is set
            initModality(Modality.APPLICATION_MODAL);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructs a new CustomAlert owned by the given window, shown as a window-modal dialog.
     * This ensures proper modality and centering relative to the owner window.
     */
    public CustomAlert(Window owner, AlertType alertType, String title, String message) {
        super(true); // Hide from taskbar
        this.alertType = alertType;
        this.title = title;
        this.message = message;

        try {
            Parent parent = Assets.load("/fxml/custom-alert.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            // Set owner and modality to keep background overlay active until dialog closes
            if (owner != null) {
                initOwner(owner);
                initModality(Modality.WINDOW_MODAL);
            } else {
                initModality(Modality.APPLICATION_MODAL);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Set title and message
        titleLabel.setText(title);
        messageLabel.setText(message);

        // Configure based on alert type
        configureAlertType();

        // Close button
        closeBtn.setOnAction(event -> {
            confirmed = false;
            close();
        });

        // OK button
        okBtn.setOnAction(event -> {
            confirmed = true;
            close();
        });

        // Cancel button (only visible for CONFIRMATION)
        cancelBtn.setOnAction(event -> {
            confirmed = false;
            close();
        });
    }

    /**
     * Configure alert appearance based on type
     */
    private void configureAlertType() {
        switch (alertType) {
            case ERROR:
                iconPath.setContent(ERROR_ICON);
                iconPath.getStyleClass().add("error-icon");
                titleLabel.getStyleClass().add("error-title");
                cancelBtn.setVisible(false);
                cancelBtn.setManaged(false);
                break;

            case INFO:
                iconPath.setContent(INFO_ICON);
                iconPath.getStyleClass().add("info-icon");
                titleLabel.getStyleClass().add("info-title");
                cancelBtn.setVisible(false);
                cancelBtn.setManaged(false);
                break;

            case WARNING:
                iconPath.setContent(WARNING_ICON);
                iconPath.getStyleClass().add("warning-icon");
                titleLabel.getStyleClass().add("warning-title");
                cancelBtn.setVisible(false);
                cancelBtn.setManaged(false);
                break;

            case CONFIRMATION:
                iconPath.setContent(QUESTION_ICON);
                iconPath.getStyleClass().add("confirmation-icon");
                titleLabel.getStyleClass().add("confirmation-title");
                okBtn.setText("Yes");
                cancelBtn.setText("No");
                cancelBtn.setVisible(true);
                cancelBtn.setManaged(true);
                break;
        }
    }

    /**
     * Check if user confirmed (clicked OK/Yes)
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Show confirmation alert centered on the owner window
     *
     * @param owner The parent window to center on (can be null, defaults to center screen)
     * @param title Title of the alert
     * @param message Message content
     * @return true if user confirmed
     */
    public static boolean showConfirmation(Window owner, String title, String message) {
        // Use owner-aware constructor to enforce window modality and automatic centering
        CustomAlert alert = new CustomAlert(owner, AlertType.CONFIRMATION, title, message);

        if (owner == null) {
            alert.centerOnScreen();
        }

        alert.showAndWait();
        return alert.isConfirmed();
    }

    /**
     * Show error alert
     */
    public static void showError(String title, String message) {
        CustomAlert alert = new CustomAlert(AlertType.ERROR, title, message);
        alert.centerOnScreen();
        alert.showAndWait();
    }

    /**
     * Show info alert
     */
    public static void showInfo(String title, String message) {
        CustomAlert alert = new CustomAlert(AlertType.INFO, title, message);
        alert.centerOnScreen();
        alert.showAndWait();
    }

    /**
     * Show warning alert
     */
    public static void showWarning(String title, String message) {
        CustomAlert alert = new CustomAlert(AlertType.WARNING, title, message);
        alert.centerOnScreen();
        alert.showAndWait();
    }


    @Override
    public List<HitSpot> getHitSpots() {
        HitSpot spot = HitSpot.builder()
                .window(this)
                .control(closeBtn)
                .close(true)
                .build();

        spot.hoveredProperty().addListener((obs, o, hovered) -> {
            if (hovered){
                spot.getControl().getStyleClass().add("hit-close-btn");
            }
            else {
                spot.getControl().getStyleClass().remove("hit-close-btn");
            }
        });

        return List.of(spot);
    }

    @Override
    public double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }

    // SVG Icons
    private static final String ERROR_ICON = "M8 0C3.6 0 0 3.6 0 8s3.6 8 8 8 8-3.6 8-8-3.6-8-8-8zm1 12H7V7h2v5zm0-6H7V4h2v2z";
    private static final String INFO_ICON = "M8 0C3.6 0 0 3.6 0 8s3.6 8 8 8 8-3.6 8-8-3.6-8-8-8zm1 12H7V7h2v5zm0-6H7V4h2v2z";
    private static final String WARNING_ICON = "M8.9 1.5C8.7 1.2 8.4 1 8 1s-.7.2-.9.5l-7 12c-.2.3-.2.7 0 1 .2.3.6.5 1 .5h14c.4 0 .8-.2 1-.5.2-.3.2-.7 0-1l-7-12zM9 13H7v-2h2v2zm0-3H7V6h2v4z";
    private static final String QUESTION_ICON = "M8 0C3.6 0 0 3.6 0 8s3.6 8 8 8 8-3.6 8-8-3.6-8-8-8zm1 13H7v-2h2v2zm1.1-5.4c-.4.4-.8.7-.8 1.5H7.7c0-1.3.7-1.8 1.1-2.2.3-.3.5-.5.5-1 0-.8-.7-1.5-1.5-1.5S6.2 5.1 6.2 6H4.7c0-2 1.6-3.5 3.5-3.5S11.7 4 11.7 6c0 1-.5 1.6-1.6 2.6z";
}