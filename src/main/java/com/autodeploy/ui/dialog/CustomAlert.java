package com.autodeploy.ui.dialog;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.ui.dialog.helper.WindowDecorationHelper;
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
import java.util.Map;
import java.util.ResourceBundle;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Dialog custom de alertă — înlocuiește Alert-ul standard JavaFX
 * cu un design consistent cu restul aplicației (undecorated, stilizat cu CSS).
 * <p>
 * Patru tipuri: ERROR, INFO, WARNING, CONFIRMATION.
 * Fiecare tip are icoană SVG, stil CSS și comportament propriu.
 * <p>
 * Utilizare:
 * <ul>
 *   <li>Fire-and-forget: {@code CustomAlert.showError("Title", "Message")}</li>
 *   <li>Cu confirmare: {@code boolean ok = CustomAlert.showConfirmation(owner, "Title", "Message")}</li>
 * </ul>
 * <p>
 * Modalitate: WINDOW_MODAL dacă are owner (blochează doar fereastra părinte),
 * APPLICATION_MODAL dacă nu (blochează toată aplicația).
 */
public class CustomAlert extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML private Button closeBtn;
    @FXML private SVGPath iconPath;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button okBtn;
    @FXML private Button cancelBtn;

    public enum AlertType {
        ERROR, INFO, WARNING, CONFIRMATION
    }

    /** Configurare per tip de alertă: icoană SVG + clase CSS pentru icon și title. */
    private record AlertTypeConfig(String svgIcon, String iconStyle, String titleStyle) {}

    private static final Map<AlertType, AlertTypeConfig> ALERT_CONFIGS = Map.of(
            AlertType.ERROR,        new AlertTypeConfig(ERROR_ICON,    "error-icon",        "error-title"),
            AlertType.INFO,         new AlertTypeConfig(INFO_ICON,     "info-icon",         "info-title"),
            AlertType.WARNING,      new AlertTypeConfig(WARNING_ICON,  "warning-icon",      "warning-title"),
            AlertType.CONFIRMATION, new AlertTypeConfig(QUESTION_ICON, "confirmation-icon", "confirmation-title")
    );

    private final AlertType alertType;
    private final String title;
    private final String message;
    private boolean confirmed = false;

    /**
     * @param owner fereastra părinte — dacă non-null, alerta e WINDOW_MODAL (blochează doar owner-ul).
     *              Dacă null, e APPLICATION_MODAL (blochează tot).
     */
    public CustomAlert(Window owner, AlertType alertType, String title, String message) {
        super(true);
        this.alertType = alertType;
        this.title = title;
        this.message = message;

        try {
            Parent parent = Assets.loadFxml("/fxml/custom-alert.fxml", this);
            setScene(new Scene(parent));

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

    public CustomAlert(AlertType alertType, String title, String message) {
        this(null, alertType, title, message);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        titleLabel.setText(title);
        messageLabel.setText(message);

        configureAlertType();

        closeBtn.setOnAction(e -> dismiss());
        okBtn.setOnAction(e -> confirm());
        cancelBtn.setOnAction(e -> dismiss());
    }

    /**
     * Aplică configurarea vizuală pe baza tipului de alertă.
     * CONFIRMATION afișează Yes/No, celelalte tipuri au doar OK.
     */
    private void configureAlertType() {
        AlertTypeConfig config = ALERT_CONFIGS.get(alertType);

        iconPath.setContent(config.svgIcon());
        iconPath.getStyleClass().add(config.iconStyle());
        titleLabel.getStyleClass().add(config.titleStyle());

        if (alertType == AlertType.CONFIRMATION) {
            okBtn.setText("Yes");
            cancelBtn.setText("No");
            cancelBtn.setVisible(true);
            cancelBtn.setManaged(true);
        } else {
            cancelBtn.setVisible(false);
            cancelBtn.setManaged(false);
        }
    }

    private void confirm() {
        confirmed = true;
        close();
    }

    private void dismiss() {
        confirmed = false;
        close();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    // --- Metode statice (convenience) ---

    public static void showError(String title, String message) {
        showAlert(AlertType.ERROR, title, message);
    }

    public static void showInfo(String title, String message) {
        showAlert(AlertType.INFO, title, message);
    }

    public static void showWarning(String title, String message) {
        showAlert(AlertType.WARNING, title, message);
    }

    /**
     * Afișează un dialog de confirmare blocant (showAndWait).
     * @return true dacă user-ul a apăsat Yes, false pentru No/Close
     */
    public static boolean showConfirmation(Window owner, String title, String message) {
        CustomAlert alert = new CustomAlert(owner, AlertType.CONFIRMATION, title, message);
        if (owner == null) {
            alert.centerOnScreen();
        }
        alert.showAndWait();
        return alert.isConfirmed();
    }

    private static void showAlert(AlertType type, String title, String message) {
        CustomAlert alert = new CustomAlert(type, title, message);
        alert.centerOnScreen();
        alert.showAndWait();
    }

    @Override
    public List<HitSpot> getHitSpots() {
        return WindowDecorationHelper.createCloseHitSpot(this, closeBtn);
    }

    @Override
    public double getTitleBarHeight() {
        return ALERT_TITLE_BAR_HEIGHT;
    }
}