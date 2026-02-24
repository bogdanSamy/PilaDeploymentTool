package com.autodeploy.ui.dialog.component;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.core.config.ThemeManager;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

/**
 * Binding bidirecțional între formularul de setări și {@link ApplicationConfig}.
 * <p>
 * Particularitate: tema se aplică instant (live preview) la selecție din combo,
 * dar se revertește la tema originală dacă user-ul anulează dialogul
 * ({@link #rollbackTheme}). Tema e persistată doar la Save.
 */
public class SettingsFormBinder {

    private final TextField antPathField;
    private final TextField urlSuffixField;
    private final TextField downloadFolderField;
    private final TextField remoteLogPathField;
    private final TextField usernameField;
    private final ComboBox<String> themeComboBox;
    private final ApplicationConfig config;

    /** Tema salvată la momentul deschiderii dialogului — pentru rollback la Cancel. */
    private String originalTheme;

    public SettingsFormBinder(TextField antPathField, TextField urlSuffixField,
                              TextField downloadFolderField, TextField remoteLogPathField,
                              TextField usernameField, ComboBox<String> themeComboBox,
                              ApplicationConfig config) {
        this.antPathField = antPathField;
        this.urlSuffixField = urlSuffixField;
        this.downloadFolderField = downloadFolderField;
        this.remoteLogPathField = remoteLogPathField;
        this.usernameField = usernameField;
        this.themeComboBox = themeComboBox;
        this.config = config;
    }

    public void initThemes() {
        themeComboBox.getItems().addAll(ThemeManager.getAvailableThemes());
    }

    /** Live preview: aplică tema instant la selecție (înainte de Save). */
    public void setupThemePreview() {
        themeComboBox.setOnAction(event -> {
            String selectedTheme = themeComboBox.getValue();
            if (selectedTheme != null && !selectedTheme.isEmpty()) {
                ThemeManager.applyTheme(selectedTheme);
            }
        });
    }

    /**
     * Încarcă configurația curentă în câmpuri.
     * Salvează tema originală pentru rollback la Cancel.
     */
    public void loadFromConfig() {
        antPathField.setText(config.getAntPath());
        urlSuffixField.setText(config.getBrowserUrlSuffix());
        downloadFolderField.setText(config.getLocalDownloadDir());
        remoteLogPathField.setText(config.getRemoteLogPath());
        usernameField.setText(config.getUsername());

        originalTheme = config.getTheme();
        themeComboBox.setValue(originalTheme);
    }

    public void saveToConfig() {
        config.setAntPath(antPathField.getText().trim());
        config.setBrowserUrlSuffix(urlSuffixField.getText().trim());
        config.setLocalDownloadDir(downloadFolderField.getText().trim());
        config.setRemoteLogPath(remoteLogPathField.getText().trim());
        config.setUsername(usernameField.getText().trim());

        String selectedTheme = themeComboBox.getValue();
        if (selectedTheme != null && !selectedTheme.isEmpty()) {
            config.setTheme(selectedTheme);
        }

        config.save();
    }

    /**
     * Revertește tema la cea originală (de la momentul deschiderii dialogului).
     * Apelat doar la Cancel — la Save, tema rămâne cea selectată.
     */
    public void rollbackTheme() {
        if (originalTheme != null && !originalTheme.isEmpty()) {
            String currentTheme = themeComboBox.getValue();
            if (!originalTheme.equals(currentTheme)) {
                ThemeManager.applyTheme(originalTheme);
            }
        }
    }
}