package com.autodeploy.ui.dialog.helper;

import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utilitar pentru deschiderea dialogurilor native de browse (fișiere și foldere).
 * <p>
 * Toate metodele setează automat directorul inițial pe baza valorii curente
 * din câmpul text (UX: user-ul nu trebuie să navigheze de la root de fiecare dată).
 * <p>
 * Folosit de: ProjectManagementDialog, SettingsDialog.
 */
public final class FileBrowserHelper {

    private FileBrowserHelper() {}

    public static void browseForFolder(Window owner, TextField targetField, String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);

        setInitialDirectoryFromPath(directoryChooser, targetField.getText());

        File selectedFolder = directoryChooser.showDialog(owner);
        if (selectedFolder != null) {
            targetField.setText(selectedFolder.getAbsolutePath());
        }
    }

    public static void browseForFile(Window owner, TextField targetField, String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        setInitialDirectoryFromParent(fileChooser, targetField.getText());

        File selectedFile = fileChooser.showOpenDialog(owner);
        if (selectedFile != null) {
            targetField.setText(selectedFile.getAbsolutePath());
        }
    }

    public static void browseForJarFile(Window owner, TextField targetField) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Library JAR");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JAR Files", "*.jar"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        setInitialDirectoryFromParent(fileChooser, targetField.getText());

        File selectedFile = fileChooser.showOpenDialog(owner);
        if (selectedFile != null) {
            targetField.setText(selectedFile.getAbsolutePath());
        }
    }

    /**
     * Variantă care returnează File-ul selectat (în loc să scrie direct în TextField).
     * Folosită de ProjectManagementDialog unde selecția declanșează și parsarea target-urilor.
     */
    public static File browseForBuildFile(Window owner, String currentPath) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Ant Build File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        if (currentPath != null && !currentPath.isEmpty()) {
            try {
                var parentPath = Paths.get(currentPath).getParent();
                if (parentPath != null && Files.exists(parentPath)) {
                    fileChooser.setInitialDirectory(parentPath.toFile());
                }
            } catch (Exception ignored) {}
        }

        return fileChooser.showOpenDialog(owner);
    }

    /**
     * Director inițial = exact calea din câmp (pentru DirectoryChooser).
     * Silently ignore dacă path-ul nu există — chooser-ul va deschide la root.
     */
    private static void setInitialDirectoryFromPath(DirectoryChooser chooser, String path) {
        if (path != null && !path.isEmpty()) {
            try {
                if (Files.exists(Paths.get(path))) {
                    chooser.setInitialDirectory(Paths.get(path).toFile());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Director inițial = parent-ul fișierului din câmp (pentru FileChooser).
     * Util când câmpul conține o cale completă spre fișier.
     */
    private static void setInitialDirectoryFromParent(FileChooser chooser, String path) {
        if (path != null && !path.isEmpty()) {
            try {
                File file = new File(path);
                if (file.getParentFile() != null && file.getParentFile().exists()) {
                    chooser.setInitialDirectory(file.getParentFile());
                }
            } catch (Exception ignored) {}
        }
    }
}