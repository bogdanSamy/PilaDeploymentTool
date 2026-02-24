package com.autodeploy.ui.dialog.component;

import com.autodeploy.ui.dialog.helper.JarFileInfo;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Panou de rezultate ale build-ului de test din ProjectManagementDialog.
 * <p>
 * Afișează status-ul build-ului (succes/fail) și lista JAR-urilor găsite
 * în directorul de output, sortate descrescător după data modificării
 * (cele mai recente primele — cel mai probabil generate de build-ul curent).
 * <p>
 * Panoul e ascuns by default și apare doar după un test build.
 * Se ascunde automat la schimbarea comenzii Ant (rezultatele devin invalide).
 */
public class BuildResultsPanel {

    private final VBox buildResultsContainer;
    private final Label buildStatusLabel;
    private final Label jarCountLabel;
    private final TableView<JarFileInfo> jarResultsTable;
    private final TableColumn<JarFileInfo, String> jarNameColumn;
    private final TableColumn<JarFileInfo, String> jarSizeColumn;
    private final TableColumn<JarFileInfo, String> jarDateColumn;
    private final ScrollPane mainScrollPane;

    public BuildResultsPanel(VBox buildResultsContainer, Label buildStatusLabel,
                             Label jarCountLabel, TableView<JarFileInfo> jarResultsTable,
                             TableColumn<JarFileInfo, String> jarNameColumn,
                             TableColumn<JarFileInfo, String> jarSizeColumn,
                             TableColumn<JarFileInfo, String> jarDateColumn,
                             ScrollPane mainScrollPane) {
        this.buildResultsContainer = buildResultsContainer;
        this.buildStatusLabel = buildStatusLabel;
        this.jarCountLabel = jarCountLabel;
        this.jarResultsTable = jarResultsTable;
        this.jarNameColumn = jarNameColumn;
        this.jarSizeColumn = jarSizeColumn;
        this.jarDateColumn = jarDateColumn;
        this.mainScrollPane = mainScrollPane;
    }

    public void setupTable() {
        jarNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));
        jarSizeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSizeFormatted()));
        jarDateColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDateModified()));
    }

    /**
     * Afișează rezultatele: scanează directorul JAR, populează tabelul,
     * și scroll-ează automat în jos pentru a face vizibil panoul.
     */
    public void showResults(boolean success, String jarDirectory) {
        List<JarFileInfo> jarFiles = scanForJarFiles(jarDirectory);

        updateStatusStyle(success);

        jarCountLabel.setText(jarFiles.size() + " JARS Generated");

        jarResultsTable.getItems().clear();
        jarResultsTable.getItems().addAll(jarFiles);

        buildResultsContainer.setVisible(true);
        buildResultsContainer.setManaged(true);

        Platform.runLater(() -> mainScrollPane.setVvalue(1.0));
    }

    public void hide() {
        buildResultsContainer.setVisible(false);
        buildResultsContainer.setManaged(false);
    }

    private void updateStatusStyle(boolean success) {
        String addStyle = success ? "build-status-success" : "build-status-error";
        String removeStyle = success ? "build-status-error" : "build-status-success";
        String statusText = success ? "✓ Success" : "✗ Failed";

        buildStatusLabel.setText(statusText);
        applyStyle(buildStatusLabel, addStyle, removeStyle);
        applyStyle(jarCountLabel, addStyle, removeStyle);
    }

    private void applyStyle(Label label, String addStyle, String removeStyle) {
        label.getStyleClass().removeAll(removeStyle);
        if (!label.getStyleClass().contains(addStyle)) {
            label.getStyleClass().add(addStyle);
        }
    }

    /**
     * Scanează directorul de output pentru JAR-uri.
     * Sortează descrescător după data modificării — cele mai recente primele,
     * astfel încât JAR-urile generate de build-ul curent apar în top.
     */
    private List<JarFileInfo> scanForJarFiles(String directoryPath) {
        List<JarFileInfo> jarFiles = new ArrayList<>();
        File directory = new File(directoryPath);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (files != null) {
                for (File file : files) {
                    jarFiles.add(new JarFileInfo(file));
                }
                jarFiles.sort((a, b) -> b.getDateModified().compareTo(a.getDateModified()));
            }
        }

        return jarFiles;
    }
}