package com.autodeploy.ui.dialog;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.ui.dialog.component.SettingsFormBinder;
import com.autodeploy.ui.dialog.helper.FileBrowserHelper;
import com.autodeploy.ui.dialog.helper.WindowDecorationHelper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.autodeploy.core.constants.Constants.TITLE_BAR_HEIGHT;

/**
 * Dialog de setări ale aplicației.
 * <p>
 * Configurări disponibile: cale Ant, URL suffix, director download,
 * cale log remote, username, temă vizuală.
 * <p>
 * Tema se aplică în preview la selecție (live preview), dar se face rollback
 * dacă user-ul apasă Cancel — implementat prin {@link SettingsFormBinder#rollbackTheme()}.
 */
public class SettingsDialog extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML private Button closeBtn;
    @FXML private TextField antPathField;
    @FXML private TextField urlSuffixField;
    @FXML private TextField downloadFolderField;
    @FXML private TextField remoteLogPathField;
    @FXML private TextField usernameField;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private Button browseAntBtn;
    @FXML private Button browseDownloadBtn;
    @FXML private Button saveBtn;
    @FXML private Button cancelBtn;

    private SettingsFormBinder formBinder;

    public SettingsDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        try {
            Parent parent = Assets.loadFxml("/fxml/settings.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            setResizable(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initComponents();
        formBinder.initThemes();
        formBinder.loadFromConfig();
        formBinder.setupThemePreview();
        bindActions();
    }

    private void initComponents() {
        formBinder = new SettingsFormBinder(
                antPathField, urlSuffixField, downloadFolderField,
                remoteLogPathField, usernameField, themeComboBox,
                ApplicationConfig.getInstance()
        );
    }

    private void bindActions() {
        closeBtn.setOnAction(e -> handleCancel());
        cancelBtn.setOnAction(e -> handleCancel());
        saveBtn.setOnAction(e -> handleSave());

        browseAntBtn.setOnAction(e ->
                FileBrowserHelper.browseForFile(this, antPathField, "Select Ant Path"));
        browseDownloadBtn.setOnAction(e ->
                FileBrowserHelper.browseForFolder(this, downloadFolderField, "Select Download Folder"));
    }

    private void handleSave() {
        formBinder.saveToConfig();
        close();
    }

    /**
     * Cancel = rollback tema la cea salvată anterior + close.
     * Necesar deoarece tema se aplică live la selecție (preview).
     */
    private void handleCancel() {
        formBinder.rollbackTheme();
        close();
    }

    @Override
    public List<HitSpot> getHitSpots() {
        return WindowDecorationHelper.createCloseHitSpot(this, closeBtn);
    }

    @Override
    public double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }
}