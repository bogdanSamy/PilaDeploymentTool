package com.autodeploy.service.scanner;

import com.autodeploy.domain.model.Project;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.autodeploy.core.constants.DeploymentConstants.*;

public class FileScannerService {

    private static final Logger LOGGER = Logger.getLogger(FileScannerService.class.getName());

    private final Project project;
    private final Consumer<String> logger;

    private FileWatcher jarWatcher;
    private FileWatcher jspWatcher;

    private VBox jarListContainer;
    private VBox jspListContainer;
    private Label jarCountLabel;
    private Label jspCountLabel;

    private final Map<String, CheckBox> jarCheckBoxMap = new LinkedHashMap<>();
    private final Map<String, CheckBox> jspCheckBoxMap = new LinkedHashMap<>();

    public FileScannerService(Project project, Consumer<String> logger) {
        this.project = project;
        this.logger = logger;
    }

    public void initializeUI(VBox jarListContainer, VBox jspListContainer,
                             Label jarCountLabel, Label jspCountLabel) {
        this.jarListContainer = jarListContainer;
        this.jspListContainer = jspListContainer;
        this.jarCountLabel = jarCountLabel;
        this.jspCountLabel = jspCountLabel;
    }

    public void setupJarSection() {
        jarListContainer.getChildren().clear();
        jarCheckBoxMap.clear();

        List<File> jarFiles = scanForFiles(project.getLocalJarPath(), JAR_EXTENSION);

        if (jarFiles.isEmpty()) {
            Label noFiles = new Label(MSG_NO_JAR_FILES);
            noFiles.setStyle(STYLE_MUTED_TEXT);
            jarListContainer.getChildren().add(noFiles);
        } else {
            jarFiles.forEach(file -> addJarFile(file.getName(), false));
        }

        updateJarCount();
    }

    public void setupJspSection() {
        jspListContainer.getChildren().clear();
        jspCheckBoxMap.clear();

        List<FileWithPath> jspFiles = scanForFilesRecursive(project.getLocalJspPath(), JSP_EXTENSION);

        if (jspFiles.isEmpty()) {
            Label noFiles = new Label(MSG_NO_JSP_FILES);
            noFiles.setStyle(STYLE_MUTED_TEXT);
            jspListContainer.getChildren().add(noFiles);
        } else {
            jspFiles.forEach(fileWithPath -> addJspFile(fileWithPath.relativePath, false));
        }

        updateJspCount();
    }

    public void startWatchers() {
        // JAR watcher (non-recursive)
        if (project.getLocalJarPath() != null && !project.getLocalJarPath().isEmpty()) {
            jarWatcher = new FileWatcher(
                    project.getLocalJarPath(),
                    JAR_EXTENSION,
                    this::handleJarChange,
                    false
            );
            jarWatcher.start();
            log("👁 Watching JAR directory: " + project.getLocalJarPath());
        }

        // JSP watcher (recursive)
        if (project.getLocalJspPath() != null && !project.getLocalJspPath().isEmpty()) {
            jspWatcher = new FileWatcher(
                    project.getLocalJspPath(),
                    JSP_EXTENSION,
                    this::handleJspChange,
                    true
            );
            jspWatcher.start();
            log("👁 Watching JSP directory (recursive): " + project.getLocalJspPath());
        }
    }

    public void stopWatchers() {
        if (jarWatcher != null) {
            jarWatcher.stop();
            jarWatcher = null;
        }
        if (jspWatcher != null) {
            jspWatcher.stop();
            jspWatcher = null;
        }
        log("✓ Stopped file watchers");
    }

    private void handleJarChange(FileWatcher.FileChangeEvent event) {
        String fileName = event.getRelativePath();

        switch (event.getType()) {
            case ADDED:
                log("➕ New JAR detected: " + fileName);
                addJarFile(fileName, true);
                break;

            case MODIFIED:
                log("✏️ JAR modified: " + fileName);
                removeJarFile(fileName);
                addJarFile(fileName, true);
                break;

            case DELETED:
                log("➖ JAR deleted: " + fileName);
                removeJarFile(fileName);
                break;
        }
    }

    private void handleJspChange(FileWatcher.FileChangeEvent event) {
        String relativePath = event.getRelativePath();

        switch (event.getType()) {
            case ADDED:
                log("➕ New JSP detected: " + relativePath);
                addJspFile(relativePath, true);
                break;

            case MODIFIED:
                log("✏️ JSP modified: " + relativePath);
                removeJspFile(relativePath);
                addJspFile(relativePath, true);
                break;

            case DELETED:
                log("➖ JSP deleted: " + relativePath);
                removeJspFile(relativePath);
                break;
        }
    }

    private void addJarFile(String fileName, boolean checked) {
        Platform.runLater(() -> {
            // Remove if already exists
            CheckBox existingCheckBox = jarCheckBoxMap.get(fileName);
            if (existingCheckBox != null) {
                jarListContainer.getChildren().remove(existingCheckBox);
            }

            CheckBox checkBox = createFileCheckBox(fileName, checked);
            jarCheckBoxMap.put(fileName, checkBox);

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateJarCount());

            // Add at top
            jarListContainer.getChildren().add(0, checkBox);

            // Highlight if checked
            if (checked) {
                checkBox.setStyle(STYLE_CHECKBOX_HIGHLIGHTED);
            } else {
                checkBox.setStyle(STYLE_CHECKBOX_DEFAULT);
            }

            updateJarCount();
        });
    }

    private void removeJarFile(String fileName) {
        Platform.runLater(() -> {
            CheckBox checkBox = jarCheckBoxMap.remove(fileName);
            if (checkBox != null) {
                jarListContainer.getChildren().remove(checkBox);
                updateJarCount();
            }
        });
    }

    private void addJspFile(String relativePath, boolean checked) {
        Platform.runLater(() -> {
            // Remove if already exists
            CheckBox existingCheckBox = jspCheckBoxMap.get(relativePath);
            if (existingCheckBox != null) {
                jspListContainer.getChildren().remove(existingCheckBox);
            }

            CheckBox checkBox = createFileCheckBox(relativePath, checked);
            jspCheckBoxMap.put(relativePath, checkBox);

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateJspCount());

            // Add at top
            jspListContainer.getChildren().add(0, checkBox);

            // Highlight if checked
            if (checked) {
                checkBox.setStyle(STYLE_CHECKBOX_HIGHLIGHTED);
            } else {
                checkBox.setStyle(STYLE_CHECKBOX_DEFAULT);
            }

            updateJspCount();
        });
    }

    private void removeJspFile(String relativePath) {
        Platform.runLater(() -> {
            CheckBox checkBox = jspCheckBoxMap.remove(relativePath);
            if (checkBox != null) {
                jspListContainer.getChildren().remove(checkBox);
                updateJspCount();
            }
        });
    }

    public void filterJspFiles(String searchText) {
        jspListContainer.getChildren().clear();

        if (searchText == null || searchText.trim().isEmpty()) {
            jspListContainer.getChildren().addAll(jspCheckBoxMap.values());
        } else {
            String lowerSearch = searchText.toLowerCase();
            List<CheckBox> filtered = jspCheckBoxMap.entrySet().stream()
                    .filter(entry -> entry.getKey().toLowerCase().contains(lowerSearch))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            jspListContainer.getChildren().addAll(filtered);
        }

        updateJspCount();
    }

    private void updateJarCount() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateJarCount);
            return;
        }

        long selected = jarCheckBoxMap.values().stream().filter(CheckBox::isSelected).count();
        long total = jarCheckBoxMap.size();
        jarCountLabel.setText(selected + " / " + total + " selected");
    }

    private void updateJspCount() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateJspCount);
            return;
        }

        long selected = jspCheckBoxMap.values().stream().filter(CheckBox::isSelected).count();
        long total = jspCheckBoxMap.size();
        jspCountLabel.setText(selected + " / " + total + " selected");
    }

    private CheckBox createFileCheckBox(String fileName, boolean checked) {
        CheckBox checkBox = new CheckBox(fileName);
        checkBox.setSelected(checked);
        checkBox.setStyle(STYLE_CHECKBOX_DEFAULT);
        return checkBox;
    }

    private List<File> scanForFiles(String directoryPath, String extension) {
        List<File> files = new ArrayList<>();

        if (directoryPath == null || directoryPath.isEmpty()) {
            return files;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log("⚠ Directory not found: " + directoryPath);
            return files;
        }

        File[] foundFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(extension));
        if (foundFiles != null) {
            files.addAll(Arrays.asList(foundFiles));
            files.sort(Comparator.comparing(File::getName));
        }

        return files;
    }

    private List<FileWithPath> scanForFilesRecursive(String directoryPath, String extension) {
        List<FileWithPath> files = new ArrayList<>();

        if (directoryPath == null || directoryPath.isEmpty()) {
            return files;
        }

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log("⚠ Directory not found: " + directoryPath);
            return files;
        }

        scanDirectoryRecursive(directory, "", extension, files);
        files.sort(Comparator.comparing(f -> f.relativePath));

        return files;
    }

    private void scanDirectoryRecursive(File directory, String relativePath,
                                        String extension, List<FileWithPath> result) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String newRelativePath = relativePath.isEmpty() ?
                        file.getName() : relativePath + "/" + file.getName();
                scanDirectoryRecursive(file, newRelativePath, extension, result);
            } else if (file.getName().toLowerCase().endsWith(extension)) {
                String fullPath = relativePath.isEmpty() ?
                        file.getName() : relativePath + "/" + file.getName();
                result.add(new FileWithPath(file, fullPath));
            }
        }
    }

    public Map<String, CheckBox> getJarCheckBoxMap() {
        return jarCheckBoxMap;
    }

    public Map<String, CheckBox> getJspCheckBoxMap() {
        return jspCheckBoxMap;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    public static class FileWithPath {
        public final File file;
        public final String relativePath;

        public FileWithPath(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }
    }
}