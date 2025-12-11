package com.autodeploy.ui.dialogs;

import com.autodeploy.assets.Assets;
import com.autodeploy.config.ApplicationConfig;
import com.autodeploy.config.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;

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

    private static final int TITLE_BAR_HEIGHT = 30;
    private final ApplicationConfig config;

    public SettingsDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.config = ApplicationConfig.getInstance();
        try {
            Parent parent = Assets.load("/fxml/settings.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            setResizable(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        themeComboBox.getItems().addAll(ThemeManager.getAvailableThemes());

        loadConfiguration();

        closeBtn.setOnAction(event -> close());
        cancelBtn.setOnAction(event -> close());
        saveBtn.setOnAction(event -> {
            saveConfiguration();
            close();
        });

        browseAntBtn.setOnAction(event -> browseForFile(antPathField, "Select Ant Path"));
        browseDownloadBtn.setOnAction(event -> browseForFolder(downloadFolderField, "Select Download Folder"));

        themeComboBox.setOnAction(event -> {
            String selectedTheme = themeComboBox.getValue();
            if (selectedTheme != null && !selectedTheme.isEmpty()) {
                ThemeManager.applyTheme(selectedTheme);
            }
        });
    }

    private void browseForFile(TextField targetField, String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);

        String currentPath = targetField.getText();
        if (!currentPath.isEmpty()) {
            try {
                if (Files.exists(Paths.get(currentPath).getParent())) {
                    fileChooser.setInitialDirectory(Paths.get(currentPath).getParent().toFile());
                }
            } catch (Exception ignored) {

            }
        }

        File selectedFile = fileChooser.showOpenDialog(this);
        if (selectedFile != null) {
            targetField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void browseForFolder(TextField targetField, String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);

        String currentPath = targetField.getText();
        if (!currentPath.isEmpty()) {
            try {
                if (Files.exists(Paths.get(currentPath))) {
                    directoryChooser.setInitialDirectory(Paths.get(currentPath).toFile());
                }
            } catch (Exception ignored) {

            }
        }

        File selectedFolder = directoryChooser.showDialog(this);
        if (selectedFolder != null) {
            targetField.setText(selectedFolder.getAbsolutePath());
        }
    }

    private void loadConfiguration() {
        antPathField.setText(config.getAntPath());
        urlSuffixField.setText(config.getBrowserUrlSuffix());
        downloadFolderField.setText(config.getLocalDownloadDir());
        remoteLogPathField.setText(config.getRemoteLogPath());
        usernameField.setText(config.getUsername());

        String savedTheme = config.getTheme();
        themeComboBox.setValue(savedTheme);
    }

    private void saveConfiguration() {
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
}