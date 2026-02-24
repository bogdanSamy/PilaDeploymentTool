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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fereastră de notificare toast — apare în colțul din dreapta-jos al ecranului.
 * <p>
 * Extinde {@link AbstractNfxUndecoratedWindow} pentru a avea o fereastră transparentă
 * fără title bar nativ (decorațiile OS sunt eliminate complet).
 * <p>
 * Trei variante de notificare:
 * <ul>
 *   <li>{@link #showSimpleNotification} — text simplu, auto-close după 3.5s</li>
 *   <li>{@link #showDownloadSuccessNotification} — cu buton "Open With...", auto-close</li>
 *   <li>{@link #showRestartServerNotification} — importantă, cu buton "Reject", FĂR�� auto-close
 *       (user-ul trebuie să interacționeze sau să închidă manual)</li>
 * </ul>
 * <p>
 * Fiecare instanță reprezintă o singură notificare. Pentru a afișa o notificare nouă,
 * se creează un controller nou (instanța veche trebuie închisă manual).
 */
public class NotificationController extends AbstractNfxUndecoratedWindow implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(NotificationController.class.getName());

    private static final double TITLE_BAR_HEIGHT = 0;
    private static final double MARGIN = 20;
    private static final double FADE_DURATION_MS = 300;

    private static final double WIDTH_STANDARD = 380;
    private static final double HEIGHT_STANDARD = 120;
    private static final double WIDTH_LARGE = 450;
    private static final double HEIGHT_LARGE = 150;
    private static final double DEFAULT_AUTO_CLOSE_SECONDS = 3.5;

    @FXML private VBox notificationContainer;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button closeBtn;
    @FXML private HBox headerBox;
    @FXML private VBox actionButtonContainer;
    @FXML private Button actionButton;

    private PauseTransition autoCloseTimer;

    /**
     * Încarcă FXML-ul și configurează fereastra în constructor.
     * Scena e transparentă (fără background OS), always-on-top, non-resizable.
     * Drag-ul e blocat în {@link .initialize} — notificarea rămâne fixă în colț.
     */
    public NotificationController() {
        super(true);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/notification.fxml"));
            loader.setController(this);
            Parent parent = loader.load();

            Scene scene = new Scene(parent, WIDTH_STANDARD, HEIGHT_STANDARD);
            scene.setFill(Color.TRANSPARENT);

            URL cssUrl = getClass().getResource("/css/notification.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            setScene(scene);
            initStyle(StageStyle.TRANSPARENT);
            setResizable(false);
            setAlwaysOnTop(true);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load notification FXML", e);
            throw new RuntimeException("Failed to load notification FXML", e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (closeBtn != null) {
            closeBtn.setOnAction(event -> closeWithAnimation());
        }

        setActionVisible(false);
        blockDragging();
    }

    public void showSimpleNotification(String title, String message) {
        setMessageVisible(true);
        setActionVisible(false);

        show(title, message, "info", WIDTH_STANDARD, HEIGHT_STANDARD, true);
    }

    public void showDownloadSuccessNotification(String fileName, Runnable onButtonClick) {
        setMessageVisible(false);
        setActionButton("Open With...", onButtonClick);

        show("File downloaded: " + fileName, "", "info", WIDTH_STANDARD, HEIGHT_STANDARD, true);
    }

    /**
     * Notificare importantă — fără auto-close.
     * User-ul trebuie să apese "Reject" sau să închidă manual.
     * Folosește dimensiuni mai mari pentru a sublinia urgența.
     */
    public void showRestartServerNotification(String message, Runnable onButtonClick) {
        setMessageVisible(true);
        setActionButton("Reject", onButtonClick);

        show("Restart Server", message, "important", WIDTH_LARGE, HEIGHT_LARGE, false);
    }

    /**
     * Metoda centrală de afișare. Configurează conținutul, stilul, dimensiunea,
     * poziția (colț dreapta-jos), și lansează animația fade-in.
     * Auto-close-ul pornește DUPĂ ce fade-in-ul se termină.
     */
    private void show(String title, String message, String cssClass,
                      double width, double height, boolean autoClose) {
        if (titleLabel != null) titleLabel.setText(title);
        if (messageLabel != null) messageLabel.setText(message);

        applyStyle(cssClass);
        resize(width, height);
        positionOnScreen(width, height);
        stopAutoCloseTimer();

        show();
        toFront();
        requestFocus();

        fadeIn(() -> {
            if (autoClose) startAutoCloseTimer();
        });
    }

    private void setMessageVisible(boolean visible) {
        if (messageLabel != null) {
            messageLabel.setVisible(visible);
            messageLabel.setManaged(visible);
        }
    }

    private void setActionVisible(boolean visible) {
        if (actionButtonContainer != null) {
            actionButtonContainer.setVisible(visible);
            actionButtonContainer.setManaged(visible);
        }
    }

    /**
     * Configurează butonul de acțiune. La click, execută callback-ul
     * și apoi închide notificarea automat.
     */
    private void setActionButton(String text, Runnable onClick) {
        if (actionButtonContainer != null && actionButton != null) {
            setActionVisible(true);
            actionButton.setText(text);
            actionButton.setOnAction(event -> {
                if (onClick != null) onClick.run();
                closeWithAnimation();
            });
        }
    }

    private void applyStyle(String cssClass) {
        if (notificationContainer != null) {
            notificationContainer.getStyleClass().removeAll(
                    "success", "error", "warning", "info", "important");
            notificationContainer.getStyleClass().add(cssClass);
        }
    }

    private void resize(double width, double height) {
        setWidth(width);
        setHeight(height);
        if (getScene() != null) {
            getScene().getRoot().resize(width, height);
        }
    }

    private void positionOnScreen(double width, double height) {
        Screen screen = Screen.getPrimary();
        double screenWidth = screen.getVisualBounds().getWidth();
        double screenHeight = screen.getVisualBounds().getHeight();
        setX(screenWidth - width - MARGIN);
        setY(screenHeight - height - MARGIN);
    }

    private void fadeIn(Runnable onFinished) {
        if (getScene() == null || getScene().getRoot() == null) {
            if (onFinished != null) onFinished.run();
            return;
        }

        getScene().getRoot().setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(
                Duration.millis(FADE_DURATION_MS), getScene().getRoot());
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        fadeIn.play();
    }

    private void closeWithAnimation() {
        if (getScene() != null && getScene().getRoot() != null) {
            FadeTransition fadeOut = new FadeTransition(
                    Duration.millis(FADE_DURATION_MS), getScene().getRoot());
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(event -> close());
            fadeOut.play();
        } else {
            close();
        }
    }

    private void startAutoCloseTimer() {
        LOGGER.fine("Auto-close timer start: " + DEFAULT_AUTO_CLOSE_SECONDS + "s");
        autoCloseTimer = new PauseTransition(Duration.seconds(DEFAULT_AUTO_CLOSE_SECONDS));
        autoCloseTimer.setOnFinished(event -> closeWithAnimation());
        autoCloseTimer.play();
    }

    private void stopAutoCloseTimer() {
        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
        }
    }

    /**
     * Blochează drag-ul pe fereastră și pe container.
     * Notificările toast nu trebuie mutate — poziția e fixă în colțul ecranului.
     * AbstractNfxUndecoratedWindow permite drag by default, deci trebuie suprascris.
     */
    private void blockDragging() {
        if (getScene() != null) {
            getScene().setOnMousePressed(event -> event.consume());
            getScene().setOnMouseDragged(event -> event.consume());
        }

        if (notificationContainer != null) {
            notificationContainer.setOnMousePressed(event -> event.consume());
            notificationContainer.setOnMouseDragged(event -> event.consume());
        }
    }

    /** Returnează listă goală — fereastra nu are zone de resize/drag. */
    @Override
    public List<HitSpot> getHitSpots() { return new ArrayList<>(); }

    /** Title bar height = 0 — fereastra nu are title bar. */
    @Override
    public double getTitleBarHeight() { return TITLE_BAR_HEIGHT; }
}