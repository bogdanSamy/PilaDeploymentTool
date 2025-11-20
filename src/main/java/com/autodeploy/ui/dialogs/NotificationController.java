package com.autodeploy.ui.dialogs;

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
import java.util.function.Consumer;

/**
 * Controller pentru notificări desktop care apar deasupra tuturor aplicațiilor
 */
public class NotificationController extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML
    private VBox notificationContainer;

    @FXML
    private Label titleLabel;

    @FXML
    private Label messageLabel;

    @FXML
    private Button closeBtn;

    @FXML
    private HBox headerBox;

    @FXML
    private VBox actionButtonContainer;

    @FXML
    private Button actionButton;

    /**
     * Înălțimea barei de titlu
     */
    private static final double TITLE_BAR_HEIGHT = 0;

    /**
     * Lățimea notificării standard
     */
    private static final double NOTIFICATION_WIDTH = 380;

    /**
     * Înălțimea notificării standard
     */
    private static final double NOTIFICATION_HEIGHT = 120;

    /**
     * Lățimea notificării mari (pentru notificări importante)
     */
    private static final double NOTIFICATION_WIDTH_LARGE = 450;

    /**
     * Înălțimea notificării mari (pentru notificări importante)
     */
    private static final double NOTIFICATION_HEIGHT_LARGE = 150;

    /**
     * Durata de afișare a notificării (în secunde)
     */
    private double displayDuration = 5.0;

    private PauseTransition autoCloseTimer;

    /**
     * Tipuri de notificări
     */
    public enum NotificationType {
        SUCCESS, ERROR, WARNING, INFO
    }

    /**
     * Variante de notificări
     */
    public enum NotificationVariant {
        SIMPLE,           // Doar informație
        WITH_ACTION,      // Informație + buton
        IMPORTANT         // Mai mare și roșie
    }

    private NotificationVariant currentVariant = NotificationVariant.SIMPLE;
    private double currentWidth = NOTIFICATION_WIDTH;
    private double currentHeight = NOTIFICATION_HEIGHT;

    /**
     * Constructor
     */
    public NotificationController() {
        super(true); // true = ascunde din taskbar

        try {
            // Încarcă FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/notification.fxml"));
            loader.setController(this);
            Parent parent = loader.load();

            // Crează scena cu fundal transparent
            Scene scene = new Scene(parent, NOTIFICATION_WIDTH, NOTIFICATION_HEIGHT);
            scene.setFill(Color.TRANSPARENT);

            // Încarcă CSS
            URL cssUrl = getClass().getResource("/notification.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            setScene(scene);

            // Configurează fereastra
            initStyle(StageStyle.TRANSPARENT);
            setResizable(false);
            setAlwaysOnTop(true);

            // IMPORTANT: Setează dimensiunile ferestrei
            setWidth(NOTIFICATION_WIDTH);
            setHeight(NOTIFICATION_HEIGHT);

            System.out.println("NotificationController inițializat cu succes!");

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Eroare la încărcarea FXML pentru notificare: " + e.getMessage(), e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("Initialize apelat!");

        // Configurează butonul de închidere
        if (closeBtn != null) {
            closeBtn.setOnAction(event -> {
                System.out.println("Buton închidere apăsat!");
                closeWithAnimation();
            });
        }

        // Ascunde butonul de acțiune implicit
        if (actionButtonContainer != null) {
            actionButtonContainer.setVisible(false);
            actionButtonContainer.setManaged(false);
        }

        // Blochează orice încercare de drag prin consumarea evenimentelor de mouse
        if (notificationContainer != null) {
            notificationContainer.setOnMousePressed(event -> {
                event.consume();
            });

            notificationContainer.setOnMouseDragged(event -> {
                event.consume();
            });
        }

        // Blochează și la nivel de scenă
        if (getScene() != null) {
            getScene().setOnMousePressed(event -> {
                event.consume();
            });

            getScene().setOnMouseDragged(event -> {
                event.consume();
            });
        }
    }

    /**
     * Afișează notificarea SIMPLĂ (doar informație)
     *
     * @param title Titlul notificării
     * @param message Mesajul notificării
     */
    public void showSimpleNotification(String title, String message) {
        showSimpleNotification(title, message, NotificationType.INFO);
    }

    /**
     * Afișează notificarea SIMPLĂ cu tip specific
     *
     * @param title Titlul notificării
     * @param message Mesajul notificării
     * @param type Tipul notificării
     */
    public void showSimpleNotification(String title, String message, NotificationType type) {
        currentVariant = NotificationVariant.SIMPLE;
        currentWidth = NOTIFICATION_WIDTH;
        currentHeight = NOTIFICATION_HEIGHT;

        // Ascunde butonul de acțiune
        if (actionButtonContainer != null) {
            actionButtonContainer.setVisible(false);
            actionButtonContainer.setManaged(false);
        }

        showNotificationInternal(title, message, type);
    }

    // Adaugă această metodă actualizată în NotificationController.java

    /**
     * Afișează notificarea CU BUTON DE ACȚIUNE (fără mesaj, doar titlu și buton)
     *
     * @param title Titlul notificării
     * @param buttonText Textul butonului
     * @param onButtonClick Acțiunea la apăsarea butonului
     */
    public void showNotificationWithAction(String title, String buttonText, Runnable onButtonClick) {
        showNotificationWithAction(title, buttonText, onButtonClick, NotificationType.INFO);
    }

    /**
     * Afișează notificarea CU BUTON DE ACȚIUNE și tip specific (fără mesaj)
     *
     * @param title Titlul notificării
     * @param buttonText Textul butonului
     * @param onButtonClick Acțiunea la apăsarea butonului
     * @param type Tipul notificării
     */
    public void showNotificationWithAction(String title, String buttonText,
                                           Runnable onButtonClick, NotificationType type) {
        currentVariant = NotificationVariant.WITH_ACTION;
        currentWidth = NOTIFICATION_WIDTH;
        currentHeight = NOTIFICATION_HEIGHT;

        // ASCUNDE mesajul complet
        if (messageLabel != null) {
            messageLabel.setVisible(false);
            messageLabel.setManaged(false);
        }

        // Configurează și afișează butonul de acțiune
        if (actionButtonContainer != null && actionButton != null) {
            actionButtonContainer.setVisible(true);
            actionButtonContainer.setManaged(true);
            actionButton.setText(buttonText);
            actionButton.setOnAction(event -> {
                if (onButtonClick != null) {
                    onButtonClick.run();
                }
                closeWithAnimation();
            });
        }

        showNotificationInternal(title, "", type); // Mesaj gol
    }

    /**
     * Afișează notificarea IMPORTANTĂ (mai mare și roșie)
     *
     * @param title Titlul notificării
     * @param message Mesajul notificării
     */
    public void showImportantNotification(String title, String message) {
        currentVariant = NotificationVariant.IMPORTANT;
        currentWidth = NOTIFICATION_WIDTH_LARGE;
        currentHeight = NOTIFICATION_HEIGHT_LARGE;

        // Ascunde butonul de acțiune
        if (actionButtonContainer != null) {
            actionButtonContainer.setVisible(false);
            actionButtonContainer.setManaged(false);
        }

        // Adaugă clasa CSS pentru notificare importantă
        if (notificationContainer != null) {
            notificationContainer.getStyleClass().add("important");
        }

        showNotificationInternal(title, message, NotificationType.ERROR);
    }

    /**
     * Metodă internă pentru afișarea notificării
     */
    private void showNotificationInternal(String title, String message, NotificationType type) {
        System.out.println("showNotification apelat cu: " + title + " - " + message + " - " + type + " - " + currentVariant);

        if (titleLabel != null) {
            titleLabel.setText(title);
        }
        if (messageLabel != null) {
            messageLabel.setText(message);
        }

        // Aplică clasa CSS corespunzătoare tipului
        if (notificationContainer != null) {
            notificationContainer.getStyleClass().removeAll("success", "error", "warning", "info", "important");
            notificationContainer.getStyleClass().add(type.name().toLowerCase());

            // Adaugă clasa important dacă e cazul
            if (currentVariant == NotificationVariant.IMPORTANT) {
                notificationContainer.getStyleClass().add("important");
            }
        }

        // Actualizează dimensiunile ferestrei și scenei
        setWidth(currentWidth);
        setHeight(currentHeight);
        if (getScene() != null) {
            getScene().getRoot().resize(currentWidth, currentHeight);
        }

        // Poziționează notificarea
        positionNotification();

        // Anulează timer-ul anterior dacă există
        if (autoCloseTimer != null) {
            autoCloseTimer.stop();
        }

        // Afișează fereastra
        System.out.println("Afișare fereastră la poziția: X=" + getX() + ", Y=" + getY());
        show();
        toFront();

        // Blochează dragul și după afișare
        blockDragging();

        // Animație de fade in
        if (getScene() != null && getScene().getRoot() != null) {
            getScene().getRoot().setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), getScene().getRoot());
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        }

        // Auto-închidere după durata specificată
        autoCloseTimer = new PauseTransition(Duration.seconds(displayDuration));
        autoCloseTimer.setOnFinished(event -> {
            System.out.println("Auto-închidere după " + displayDuration + " secunde");
            closeWithAnimation();
        });
        autoCloseTimer.play();
    }

    /**
     * Afișează notificarea cu animație (menține compatibilitatea cu codul vechi)
     *
     * @param title Titlul notificării
     * @param message Mesajul notificării
     */
    public void showNotification(String title, String message) {
        showSimpleNotification(title, message, NotificationType.INFO);
    }

    /**
     * Afișează notificarea cu animație și tip specific (menține compatibilitatea cu codul vechi)
     *
     * @param title Titlul notificării
     * @param message Mesajul notificării
     * @param type Tipul notificării
     */
    public void showNotification(String title, String message, NotificationType type) {
        showSimpleNotification(title, message, type);
    }

    /**
     * Blochează funcționalitatea de drag
     */
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

    /**
     * Închide notificarea cu animație de fade out
     */
    private void closeWithAnimation() {
        if (getScene() != null && getScene().getRoot() != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), getScene().getRoot());
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(event -> {
                close();
                System.out.println("Fereastră închisă!");
            });
            fadeOut.play();
        } else {
            close();
        }
    }

    /**
     * Poziționează notificarea în colțul din dreapta jos al ecranului
     */
    private void positionNotification() {
        Screen screen = Screen.getPrimary();
        double screenWidth = screen.getVisualBounds().getWidth();
        double screenHeight = screen.getVisualBounds().getHeight();

        // Margine de 20 pixeli de la marginea ecranului
        double margin = 20;

        double xPos = screenWidth - currentWidth - margin;
        double yPos = screenHeight - currentHeight - margin;

        setX(xPos);
        setY(yPos);

        System.out.println("Poziționare notificare: X=" + xPos + ", Y=" + yPos);
        System.out.println("Dimensiuni: " + currentWidth + "x" + currentHeight);
    }

    /**
     * Setează durata de afișare a notificării
     *
     * @param seconds Durata în secunde
     */
    public void setDisplayDuration(double seconds) {
        this.displayDuration = seconds;
    }

    @Override
    public List<HitSpot> getHitSpots() {
        return new ArrayList<>();
    }

    @Override
    public double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }
}