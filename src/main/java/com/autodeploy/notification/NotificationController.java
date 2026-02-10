package com.autodeploy.notification;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class NotificationController extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML private VBox notificationContainer;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button closeBtn;
    @FXML private HBox headerBox;
    @FXML private VBox actionButtonContainer;
    @FXML private Button actionButton;

    private static final double TITLE_BAR_HEIGHT = 0;
    private static final double NOTIFICATION_WIDTH = 380;
    private static final double NOTIFICATION_HEIGHT = 120;
    private static final double NOTIFICATION_WIDTH_LARGE = 450;
    private static final double NOTIFICATION_HEIGHT_LARGE = 150;
    private double displayDuration = 3.5;

    private PauseTransition autoCloseTimer;

    public enum NotificationVariant {
        SIMPLE("info"),
        WITH_ACTION("info"),
        IMPORTANT("error");

        private final String cssClass;

        NotificationVariant(String cssClass) {
            this.cssClass = cssClass;
        }

        public String getCssClass() {
            return cssClass;
        }
    }

    private NotificationVariant currentVariant = NotificationVariant.SIMPLE;
    private double currentWidth = NOTIFICATION_WIDTH;
    private double currentHeight = NOTIFICATION_HEIGHT;

    public NotificationController() {
        super(true); // true = ascunde din taskbar

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/notification.fxml"));
            loader.setController(this);
            Parent parent = loader.load();

            Scene scene = new Scene(parent, NOTIFICATION_WIDTH, NOTIFICATION_HEIGHT);
            scene.setFill(Color.TRANSPARENT);

            URL cssUrl = getClass().getResource("/css/notification.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            setScene(scene);

            initStyle(StageStyle.TRANSPARENT);
            setResizable(false);
            setAlwaysOnTop(true);

            setWidth(NOTIFICATION_WIDTH);
            setHeight(NOTIFICATION_HEIGHT);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Eroare la încărcarea FXML pentru notificare: " + e.getMessage(), e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (closeBtn != null) {
            closeBtn.setOnAction(event -> closeWithAnimation());
        }

        if (actionButtonContainer != null) {
            actionButtonContainer.setVisible(false);
            actionButtonContainer.setManaged(false);
        }

        if (notificationContainer != null) {
            notificationContainer.setOnMousePressed(event -> event.consume());
            notificationContainer.setOnMouseDragged(event -> event.consume());
        }

        if (getScene() != null) {
            getScene().setOnMousePressed(event -> event.consume());
            getScene().setOnMouseDragged(event -> event.consume());
        }
    }

    /**
     * Show simple notification that auto-closes
     */
    public void showSimpleNotification(String title, String message) {
        currentVariant = NotificationVariant.SIMPLE;
        currentWidth = NOTIFICATION_WIDTH;
        currentHeight = NOTIFICATION_HEIGHT;

        if (actionButtonContainer != null) {
            actionButtonContainer.setVisible(false);
            actionButtonContainer.setManaged(false);
        }

        if (messageLabel != null) {
            messageLabel.setVisible(true);
            messageLabel.setManaged(true);
        }

        // Standard notifications auto-close
        showNotificationInternal(title, message, currentVariant, true);
    }

    /**
     * Specific method for RESTART PENDING (Requester view)
     * Does NOT auto-close because we need to see the countdown
     */
    public void showPersistentNotification(String title, String message) {
        currentVariant = NotificationVariant.SIMPLE;
        currentWidth = NOTIFICATION_WIDTH;
        currentHeight = NOTIFICATION_HEIGHT;

        if (actionButtonContainer != null) {
            actionButtonContainer.setVisible(false);
            actionButtonContainer.setManaged(false);
        }

        if (messageLabel != null) {
            messageLabel.setVisible(true);
            messageLabel.setManaged(true);
        }

        showNotificationInternal(title, message, currentVariant, false);
    }

    public void showDownloadSuccessNotification(String fileName, Runnable onButtonClick) {
        currentVariant = NotificationVariant.WITH_ACTION;
        currentWidth = NOTIFICATION_WIDTH;
        currentHeight = NOTIFICATION_HEIGHT;

        if (messageLabel != null) {
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
        }

        if (actionButtonContainer != null && actionButton != null) {
            actionButtonContainer.setVisible(true);
            actionButtonContainer.setManaged(true);
            actionButton.setText("Open With...");
            actionButton.setOnAction(event -> {
                if (onButtonClick != null) onButtonClick.run();
                closeWithAnimation();
            });
        }

        showNotificationInternal("File downloaded: " + fileName, "", currentVariant, true);
    }

    public void showRestartServerNotification(String message, Runnable onButtonClick) {
        currentVariant = NotificationVariant.IMPORTANT;
        currentWidth = NOTIFICATION_WIDTH_LARGE;
        currentHeight = NOTIFICATION_HEIGHT_LARGE;

        if (messageLabel != null) {
            messageLabel.setVisible(true);
            messageLabel.setManaged(true);
        }

        if (actionButtonContainer != null && actionButton != null) {
            actionButtonContainer.setVisible(true);
            actionButtonContainer.setManaged(true);
            actionButton.setText("Reject");
            actionButton.setOnAction(event -> {
                if (onButtonClick != null) onButtonClick.run();
                closeWithAnimation();
            });
        }

        // Restart Reject option = NO auto-close
        showNotificationInternal("Restart Server", message, currentVariant, false);
    }

    private void showNotificationInternal(String title, String message, NotificationVariant variant, boolean autoClose) {
        if (titleLabel != null) titleLabel.setText(title);
        if (messageLabel != null) messageLabel.setText(message);

        if (notificationContainer != null) {
            notificationContainer.getStyleClass().removeAll("success", "error", "warning", "info", "important");
            notificationContainer.getStyleClass().add(variant.getCssClass());
            if (variant == NotificationVariant.IMPORTANT) {
                notificationContainer.getStyleClass().add("important");
            }
        }

        setWidth(currentWidth);
        setHeight(currentHeight);
        if (getScene() != null) {
            getScene().getRoot().resize(currentWidth, currentHeight);
        }

        positionNotification();

        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
        }

        show();
        toFront();
        requestFocus();
        blockDragging();

        if (getScene() != null && getScene().getRoot() != null) {
            getScene().getRoot().setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), getScene().getRoot());
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            // ONLY start timer if autoClose is true
            if (autoClose) {
                fadeIn.setOnFinished(fadeEvent -> startAutoCloseTimer());
            }
            fadeIn.play();
        } else {
            // ONLY start timer if autoClose is true
            if (autoClose) {
                startAutoCloseTimer();
            }
        }
    }

    private void startAutoCloseTimer() {
        System.out.println("🕐 Timer start: " + displayDuration + "s");
        autoCloseTimer = new PauseTransition(Duration.seconds(displayDuration));
        autoCloseTimer.setOnFinished(event -> closeWithAnimation());
        autoCloseTimer.play();
    }

    // Legacy support
    public void showNotification(String title, String message) {
        showSimpleNotification(title, message);
    }

    private void blockDragging() {
        if (getScene() != null) {
            getScene().setOnMousePressed(event -> event.consume());
            getScene().setOnMouseDragged(event -> event.consume());
            if (getScene().getRoot() != null) {
                getScene().getRoot().setOnMousePressed(event -> event.consume());
                getScene().getRoot().setOnMouseDragged(event -> event.consume());
            }
        }
    }

    private void closeWithAnimation() {
        if (getScene() != null && getScene().getRoot() != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), getScene().getRoot());
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(event -> close());
            fadeOut.play();
        } else {
            close();
        }
    }

    private void positionNotification() {
        Screen screen = Screen.getPrimary();
        double screenWidth = screen.getVisualBounds().getWidth();
        double screenHeight = screen.getVisualBounds().getHeight();
        double margin = 20;
        double xPos = screenWidth - currentWidth - margin;
        double yPos = screenHeight - currentHeight - margin;
        setX(xPos);
        setY(yPos);
    }

    public void setDisplayDuration(double seconds) {
        this.displayDuration = seconds;
    }

    @Override
    public List<HitSpot> getHitSpots() { return new ArrayList<>(); }

    @Override
    public double getTitleBarHeight() { return TITLE_BAR_HEIGHT; }
}