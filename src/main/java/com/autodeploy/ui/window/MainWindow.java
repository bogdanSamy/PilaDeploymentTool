package com.autodeploy.ui.window;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.ui.tab.SessionTab;
import com.autodeploy.ui.tab.SessionTabManager;
import com.autodeploy.ui.window.component.TitleBarManager;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import xss.it.nfx.NfxStage;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Fereastra principală unică a aplicației — singurul {@link NfxStage} din app flow.
 * <p>
 * Deține:
 * <ul>
 *   <li>Title bar shared: iconiță, titlu, tab strip, buton "New Tab", controale fereastră</li>
 *   <li>Content area: {@link StackPane} care arată conținutul tab-ului activ</li>
 *   <li>{@link SessionTabManager}: gestionează lista de tab-uri și tab-ul activ</li>
 * </ul>
 * <p>
 * <b>Client areas:</b> toate butoanele din tab strip și butonul "New Tab" sunt
 * înregistrate via {@link #addClientAreas(Region...)} astfel încât click-urile pe
 * ele să nu fie consumate de logica drag-to-move a NfxStage.
 */
public class MainWindow extends NfxStage implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());

    @FXML private Button closeBtn, maxBtn, minBtn;
    @FXML private SVGPath maxShape;
    @FXML private ImageView iconView;
    @FXML private Label title;
    @FXML private HBox tabStripBox;
    @FXML private MFXButton newTabBtn;
    @FXML private StackPane contentArea;

    private SessionTabManager tabManager;
    private TitleBarManager titleBarManager;

    /** Mapare tab → chip HBox din title bar, pentru actualizare și eliminare. */
    private final Map<SessionTab, HBox> tabChips = new HashMap<>();

    public MainWindow() {
        super();
        try {
            initializeWindow();
        } catch (IOException e) {
            LOGGER.severe("Failed to initialize MainWindow: " + e.getMessage());
            throw new RuntimeException("Failed to initialize MainWindow", e);
        }
    }

    private void initializeWindow() throws IOException {
        getIcons().add(new Image(Assets.location("/logo.png").toExternalForm()));
        Parent parent = Assets.loadFxml("/fxml/main-window.fxml", this);
        setScene(new Scene(parent));
        setTitle(WINDOW_TITLE);
        setOnCloseRequest(event -> handleWindowClose());

        tabManager = new SessionTabManager(this);
        tabManager.openSelectionTab();
    }

    /**
     * Faza 2 (apelată de FXMLLoader, ÎNAINTE de finalul constructorului).
     * Configurează title bar-ul și înregistrează controalele ca client areas.
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        titleBarManager = new TitleBarManager(
                this, closeBtn, maxBtn, minBtn, maxShape, iconView, title,
                (close, max, min) -> {
                    setCloseControl(close);
                    setMaxControl(max);
                    setMinControl(min);
                }
        );
        titleBarManager.setup();

        // Înregistrează newTabBtn și tabStripBox ca client areas — fără asta,
        // NfxStage ar consuma click-urile ca window-drag deoarece se află în title bar.
        addClientAreas(newTabBtn, tabStripBox);

        newTabBtn.setOnAction(e -> tabManager.openSelectionTab());
    }

    // -------------------------------------------------------------------------
    // Metode apelate de SessionTabManager pentru sincronizarea UI
    // -------------------------------------------------------------------------

    /** Adaugă conținutul tab-ului în StackPane-ul content area (inițial ascuns). */
    public void addTabContent(SessionTab tab) {
        Parent root = tab.getContentRoot();
        root.setVisible(false);
        root.setManaged(false);
        contentArea.getChildren().add(root);
    }

    /** Elimină conținutul tab-ului din StackPane-ul content area. */
    public void removeTabContent(SessionTab tab) {
        contentArea.getChildren().remove(tab.getContentRoot());
    }

    /**
     * Face tab-ul dat vizibil și ascunde toate celelalte.
     * Background tasks (connections, watchers) ale tab-urilor inactive continuă să ruleze.
     */
    public void showTab(SessionTab tab) {
        for (var child : contentArea.getChildren()) {
            child.setVisible(false);
            child.setManaged(false);
        }
        tab.getContentRoot().setVisible(true);
        tab.getContentRoot().setManaged(true);
    }

    /** Adaugă un chip pentru tab-ul nou în tab strip și îl înregistrează ca client area. */
    public void addTabChip(SessionTab tab) {
        HBox chip = createTabChip(tab);
        tabChips.put(tab, chip);
        tabStripBox.getChildren().add(chip);

        // Înregistrează chip-ul și butonul "×" ca client areas
        Button chipCloseBtn = (Button) chip.getUserData();
        addClientAreas(chip, chipCloseBtn);
    }

    /** Elimină chip-ul tab-ului din tab strip. */
    public void removeTabChip(SessionTab tab) {
        HBox chip = tabChips.remove(tab);
        if (chip != null) {
            tabStripBox.getChildren().remove(chip);
        }
    }

    /** Actualizează stilul chip-urilor: evidențiază tab-ul activ. */
    public void updateTabChipSelection(SessionTab activeTab) {
        tabChips.forEach((tab, chip) -> {
            Label nameLabel = (Label) chip.getProperties().get("nameLabel");
            if (tab == activeTab) {
                chip.setStyle(CHIP_ACTIVE_STYLE);
                if (nameLabel != null) nameLabel.setStyle(CHIP_LABEL_ACTIVE_STYLE);
            } else {
                chip.setStyle(CHIP_INACTIVE_STYLE);
                if (nameLabel != null) nameLabel.setStyle(CHIP_LABEL_INACTIVE_STYLE);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Creare chip tab
    // -------------------------------------------------------------------------

    private HBox createTabChip(SessionTab tab) {
        Label nameLabel = new Label();
        nameLabel.textProperty().bind(tab.displayNameProperty());
        nameLabel.setStyle(CHIP_LABEL_INACTIVE_STYLE);
        nameLabel.setMaxWidth(130);

        Button chipCloseBtn = new Button("×");
        chipCloseBtn.setStyle(CHIP_CLOSE_BTN_STYLE);
        chipCloseBtn.setOnAction(e -> tabManager.closeTab(tab));

        HBox chip = new HBox(4, nameLabel, chipCloseBtn);
        chip.setStyle(CHIP_INACTIVE_STYLE);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setOnMouseClicked(e -> tabManager.setActiveTab(tab));
        chip.setMinHeight(28);
        chip.setMaxHeight(28);
        chip.getProperties().put("nameLabel", nameLabel);
        chip.setUserData(chipCloseBtn);

        return chip;
    }

    // -------------------------------------------------------------------------
    // Window close
    // -------------------------------------------------------------------------

    private void handleWindowClose() {
        tabManager.closeAllTabs();
    }

    @Override
    protected double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }

    // -------------------------------------------------------------------------
    // Tab chip styles (inline — hover pseudo-class nu e disponibil în inline CSS)
    // -------------------------------------------------------------------------

    private static final String CHIP_ACTIVE_STYLE =
            "-fx-background-color: rgba(255,255,255,0.25); " +
            "-fx-background-radius: 4px; " +
            "-fx-padding: 4 8 4 8; " +
            "-fx-cursor: hand;";

    private static final String CHIP_INACTIVE_STYLE =
            "-fx-background-color: rgba(255,255,255,0.10); " +
            "-fx-background-radius: 4px; " +
            "-fx-padding: 4 8 4 8; " +
            "-fx-cursor: hand;";

    private static final String CHIP_LABEL_ACTIVE_STYLE =
            "-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;";

    private static final String CHIP_LABEL_INACTIVE_STYLE =
            "-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 12px;";

    private static final String CHIP_CLOSE_BTN_STYLE =
            "-fx-background-color: transparent; " +
            "-fx-text-fill: rgba(255,255,255,0.85); " +
            "-fx-font-size: 14px; " +
            "-fx-padding: 0 2 0 2; " +
            "-fx-cursor: hand; " +
            "-fx-min-width: 18px; " +
            "-fx-pref-width: 18px;";
}
