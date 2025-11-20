/*
 * Copyright © 2024. XTREME SOFTWARE SOLUTIONS
 *
 * All rights reserved. Unauthorized use, reproduction, or distribution
 * of this software or any portion of it is strictly prohibited and may
 * result in severe civil and criminal penalties. This code is the sole
 * proprietary of XTREME SOFTWARE SOLUTIONS.
 *
 * Commercialization, redistribution, and use without explicit permission
 * from XTREME SOFTWARE SOLUTIONS, are expressly forbidden.
 */

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

/**
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class SettingsDialog extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML
    private Button closeBtn;

    @FXML
    private TextField antPathField;

    @FXML
    private TextField urlSuffixField;

    @FXML
    private TextField downloadFolderField;

    @FXML
    private TextField remoteLogPathField;

    @FXML
    private TextField usernameField;

    @FXML
    private ComboBox<String> themeComboBox;

    @FXML
    private Button browseAntBtn;

    @FXML
    private Button browseDownloadBtn;

    @FXML
    private Button saveBtn;

    @FXML
    private Button cancelBtn;

    /**
     * The height of the title bar.
     */
    private static final int TITLE_BAR_HEIGHT = 30;

    /**
     * Application configuration instance
     */
    private final ApplicationConfig config;

    /**
     * Constructs a new instance of SettingsDialog with an option to hide from the taskbar.
     *
     * @param hideFromTaskBar Indicates whether the dialog should be hidden from the taskbar.
     */
    public SettingsDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.config = ApplicationConfig.getInstance();
        try {
            Parent parent = Assets.load("/settings.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            setResizable(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize theme ComboBox
        themeComboBox.getItems().addAll(ThemeManager.getAvailableThemes());

        // Load existing configuration
        loadConfiguration();

        // Close button
        closeBtn.setOnAction(event -> close());

        // Cancel button
        cancelBtn.setOnAction(event -> close());

        // Save button
        saveBtn.setOnAction(event -> {
            saveConfiguration();
            close();
        });

        // Browse buttons
        browseAntBtn.setOnAction(event -> browseForFile(antPathField, "Select Ant Path"));
        browseDownloadBtn.setOnAction(event -> browseForFolder(downloadFolderField, "Select Download Folder"));

        // Theme change listener - apply immediately when selected
        themeComboBox.setOnAction(event -> {
            String selectedTheme = themeComboBox.getValue();
            if (selectedTheme != null && !selectedTheme.isEmpty()) {
                ThemeManager.applyTheme(selectedTheme);
            }
        });
    }

    /**
     * Browse for a file
     */
    private void browseForFile(TextField targetField, String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);

        // Set initial directory if path exists
        String currentPath = targetField.getText();
        if (!currentPath.isEmpty()) {
            try {
                if (Files.exists(Paths.get(currentPath).getParent())) {
                    fileChooser.setInitialDirectory(Paths.get(currentPath).getParent().toFile());
                }
            } catch (Exception e) {
                // Ignore if path is invalid
            }
        }

        File selectedFile = fileChooser.showOpenDialog(this);
        if (selectedFile != null) {
            targetField.setText(selectedFile.getAbsolutePath());
        }
    }

    /**
     * Browse for a folder
     */
    private void browseForFolder(TextField targetField, String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);

        // Set initial directory if path exists
        String currentPath = targetField.getText();
        if (!currentPath.isEmpty()) {
            try {
                if (Files.exists(Paths.get(currentPath))) {
                    directoryChooser.setInitialDirectory(Paths.get(currentPath).toFile());
                }
            } catch (Exception e) {
                // Ignore if path is invalid
            }
        }

        File selectedFolder = directoryChooser.showDialog(this);
        if (selectedFolder != null) {
            targetField.setText(selectedFolder.getAbsolutePath());
        }
    }

    /**
     * Load configuration from ApplicationConfig
     */
    private void loadConfiguration() {
        antPathField.setText(config.getAntPath());
        urlSuffixField.setText(config.getBrowserUrlSuffix());
        downloadFolderField.setText(config.getLocalDownloadDir());
        remoteLogPathField.setText(config.getRemoteLogPath());
        usernameField.setText(config.getUsername());

        // Load theme
        String savedTheme = config.getTheme();
        themeComboBox.setValue(savedTheme);
    }

    /**
     * Save configuration to ApplicationConfig
     */
    private void saveConfiguration() {
        // Set all values
        config.setAntPath(antPathField.getText().trim());
        config.setBrowserUrlSuffix(urlSuffixField.getText().trim());
        config.setLocalDownloadDir(downloadFolderField.getText().trim());
        config.setRemoteLogPath(remoteLogPathField.getText().trim());
        config.setUsername(usernameField.getText().trim());

        // Save theme
        String selectedTheme = themeComboBox.getValue();
        if (selectedTheme != null && !selectedTheme.isEmpty()) {
            config.setTheme(selectedTheme);
        }

        // Persist to file
        config.save();
    }

    /**
     * Retrieves the list of hit spots.
     *
     * @return The list of hit spots.
     */
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

    /**
     * Retrieves the height of the title bar.
     *
     * @return The height of the title bar.
     */
    @Override
    public double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }
}