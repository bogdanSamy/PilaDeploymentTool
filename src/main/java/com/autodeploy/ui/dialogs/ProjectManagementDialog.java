package com.autodeploy.ui.dialogs;

import com.autodeploy.assets.Assets;
import com.autodeploy.config.ProjectManager;
import com.autodeploy.model.Project;
import com.autodeploy.helper.AntFileParser;
import com.autodeploy.services.BuildService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

public class ProjectManagementDialog extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML private ScrollPane mainScrollPane;
    @FXML private Button closeBtn;
    @FXML private TableView<Project> projectsTable;
    @FXML private TableColumn<Project, String> nameColumn;
    @FXML private TextField nameField;
    @FXML private TextField localJarPathField;
    @FXML private TextField localJspPathField;
    @FXML private TextField remoteJarPathField;
    @FXML private TextField remoteJspPathField;
    @FXML private TextField buildFilePathField;
    @FXML private ComboBox<String> antTargetComboBox;

    // Libraries section
    @FXML private HBox librariesHeader;
    @FXML private Button addLibraryInlineBtn;
    @FXML private VBox librariesContainer;
    @FXML private HBox librariesFooter;
    @FXML private Button addLibraryBtn;

    @FXML private TextArea antCommandArea;
    @FXML private Button regenerateCommandBtn;
    @FXML private Button testBuildBtn;

    // Build results section
    @FXML private VBox buildResultsContainer;
    @FXML private Label buildStatusLabel;
    @FXML private Label jarCountLabel;

    @FXML private TableView<JarFileInfo> jarResultsTable;
    @FXML private TableColumn<JarFileInfo, String> jarNameColumn;
    @FXML private TableColumn<JarFileInfo, String> jarSizeColumn;
    @FXML private TableColumn<JarFileInfo, String> jarDateColumn;
    @FXML private Button hideBuildResultsBtn;

    @FXML private Button browseLocalJarBtn;
    @FXML private Button browseLocalJspBtn;
    @FXML private Button browseBuildFileBtn;
    @FXML private Button addBtn;
    @FXML private Button updateBtn;
    @FXML private Button deleteBtn;
    @FXML private Button clearBtn;
    @FXML private Button closeDialogBtn;

    private static final int TITLE_BAR_HEIGHT = 30;
    private final ProjectManager projectManager;
    private final ObservableList<Project> projectsList;
    private Project selectedProject;

    // Lista de rânduri pentru libraries
    private final List<LibraryRow> libraryRows = new ArrayList<>();

    /**
     * Clasă helper pentru un rând de library
     */
    private class LibraryRow {
        final HBox container;
        final TextField pathField;
        final Button browseBtn;
        final Button removeBtn;

        LibraryRow(HBox container, TextField pathField, Button browseBtn, Button removeBtn) {
            this.container = container;
            this.pathField = pathField;
            this.browseBtn = browseBtn;
            this.removeBtn = removeBtn;
        }

        String getPath() {
            return pathField.getText().trim();
        }

        boolean isValid() {
            return !getPath().isEmpty();
        }
    }

    /**
     * Clasă helper pentru informații despre JAR
     */
    public static class JarFileInfo {
        private final String name;
        private final long size;
        private final String sizeFormatted;
        private final String dateModified;

        public JarFileInfo(File file) {
            this.name = file.getName();
            this.size = file.length();
            this.sizeFormatted = formatSize(file.length());
            this.dateModified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(file.lastModified()));
        }

        public String getName() { return name; }
        public long getSize() { return size; }
        public String getSizeFormatted() { return sizeFormatted; }
        public String getDateModified() { return dateModified; }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    public ProjectManagementDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.projectManager = ProjectManager.getInstance();
        this.projectsList = FXCollections.observableArrayList();
        try {
            Parent parent = Assets.load("/fxml/project-management.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            setResizable(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupJarResultsTable();
        loadProjects();

        // Close buttons
        closeBtn.setOnAction(event -> close());
        closeDialogBtn.setOnAction(event -> close());

        // Browse buttons
        browseLocalJarBtn.setOnAction(event -> browseForFolder(localJarPathField, "Select Local JAR Directory"));
        browseLocalJspBtn.setOnAction(event -> browseForFolder(localJspPathField, "Select Local JSP Directory"));
        browseBuildFileBtn.setOnAction(event -> browseForBuildFile());

        // Library buttons
        addLibraryInlineBtn.setOnAction(event -> {
            addLibraryRow("");
            updateLibrariesVisibility();
        });

        addLibraryBtn.setOnAction(event -> {
            addLibraryRow("");
            updateLibrariesVisibility();
        });

        // Regenerate command button
        regenerateCommandBtn.setOnAction(event -> generateCommand());

        // Test build button
        testBuildBtn.setOnAction(event -> runTestBuild());

        hideBuildResultsBtn.setOnAction(event -> hideBuildResults());

        // Action buttons
        addBtn.setOnAction(event -> addProject());
        updateBtn.setOnAction(event -> updateProject());
        deleteBtn.setOnAction(event -> deleteProject());
        clearBtn.setOnAction(event -> clearFields());

        // Table selection listener
        projectsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                loadProjectToFields(newSelection);
                selectedProject = newSelection;
                updateBtn.setDisable(false);
                deleteBtn.setDisable(false);
            } else {
                selectedProject = null;
                updateBtn.setDisable(true);
                deleteBtn.setDisable(true);
            }
        });

        // Build file listener - parsează targets
        buildFilePathField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                updateAntTargets(buildFilePathField.getText());
            }
        });

        // Listener pentru regenerarea comenzii când se schimbă build file
        buildFilePathField.textProperty().addListener((obs, old, newVal) -> generateCommand());

        // Listener pentru regenerarea comenzii când se schimbă target
        antTargetComboBox.valueProperty().addListener((obs, old, newVal) -> generateCommand());

        projectsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                loadProjectToFields(newSelection);
                selectedProject = newSelection;
                updateBtn.setDisable(false);
                deleteBtn.setDisable(false);
            } else {
                selectedProject = null;
                updateBtn.setDisable(true);
                deleteBtn.setDisable(true);
            }
            // Ascunde rezultatele build-ului
            hideBuildResults();
        });

        // Ascunde build results când se modifică comanda
        antCommandArea.textProperty().addListener((obs, old, newVal) -> hideBuildResults());

        // Initially disable update/delete buttons
        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);

        // Inițializează vizibilitatea libraries
        updateLibrariesVisibility();

        System.out.println("ProjectManagementDialog initialized with " + projectsList.size() + " projects");
    }

    /**
     * Ascunde secțiunea Build Results
     */
    private void hideBuildResults() {
        buildResultsContainer.setVisible(false);
        buildResultsContainer.setManaged(false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LIBRARY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    private void updateLibrariesVisibility() {
        boolean hasLibraries = !libraryRows.isEmpty();

        addLibraryInlineBtn.setVisible(!hasLibraries);
        addLibraryInlineBtn.setManaged(!hasLibraries);

        librariesContainer.setVisible(hasLibraries);
        librariesContainer.setManaged(hasLibraries);
        librariesFooter.setVisible(hasLibraries);
        librariesFooter.setManaged(hasLibraries);
    }

    private LibraryRow addLibraryRow(String path) {
        double scrollPosition = mainScrollPane.getVvalue();

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        TextField pathField = new TextField(path);
        pathField.setPromptText("C:\\path\\to\\library.jar");
        pathField.getStyleClass().addAll("field-input", "library-input");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse");
        browseBtn.getStyleClass().add("lib-browse-btn");
        browseBtn.setMinWidth(70);

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("remove-lib-btn");

        row.getChildren().addAll(pathField, browseBtn, removeBtn);

        LibraryRow libRow = new LibraryRow(row, pathField, browseBtn, removeBtn);
        libraryRows.add(libRow);
        librariesContainer.getChildren().add(row);

        browseBtn.setOnAction(event -> browseForSingleLibrary(pathField));

        removeBtn.setOnAction(event -> removeLibraryRow(libRow));

        pathField.textProperty().addListener((obs, old, newVal) -> generateCommand());

        generateCommand();

        Platform.runLater(() -> {
            mainScrollPane.setVvalue(scrollPosition);
            pathField.requestFocus();
        });

        return libRow;
    }

    private void removeLibraryRow(LibraryRow row) {
        double scrollPosition = mainScrollPane.getVvalue();

        libraryRows.remove(row);
        librariesContainer.getChildren().remove(row.container);
        generateCommand();
        updateLibrariesVisibility();

        Platform.runLater(() -> mainScrollPane.setVvalue(scrollPosition));
    }

    private void clearLibraryRows() {
        libraryRows.clear();
        librariesContainer.getChildren().clear();
        updateLibrariesVisibility();
    }

    private List<String> getLibrariesFromUI() {
        List<String> libraries = new ArrayList<>();
        for (LibraryRow row : libraryRows) {
            if (row.isValid()) {
                libraries.add(row.getPath());
            }
        }
        return libraries;
    }

    private void loadLibrariesToUI(List<String> libraries) {
        clearLibraryRows();
        if (libraries != null) {
            for (String lib : libraries) {
                if (lib != null && !lib.trim().isEmpty()) {
                    addLibraryRow(lib);
                }
            }
        }
        updateLibrariesVisibility();
    }

    private void browseForSingleLibrary(TextField targetField) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Library JAR");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JAR Files", "*.jar"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        String currentPath = targetField.getText();
        if (!currentPath.isEmpty()) {
            try {
                File currentFile = new File(currentPath);
                if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
                    fileChooser.setInitialDirectory(currentFile.getParentFile());
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        File selectedFile = fileChooser.showOpenDialog(this);
        if (selectedFile != null) {
            targetField.setText(selectedFile.getAbsolutePath());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMAND GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    private void generateCommand() {
        StringBuilder command = new StringBuilder();
        command.append("ant");

        for (String lib : getLibrariesFromUI()) {
            command.append(" -lib \"").append(lib).append("\"");
        }

        String buildFile = buildFilePathField.getText();
        if (buildFile != null && !buildFile.trim().isEmpty()) {
            command.append(" -f \"").append(buildFile.trim()).append("\"");
        }

        String target = antTargetComboBox.getValue();
        if (target != null && !target.trim().isEmpty()) {
            command.append(" ").append(target);
        }

        antCommandArea.setText(command.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST BUILD
    // ═══════════════════════════════════════════════════════════════════════════

    private void setupJarResultsTable() {
        jarNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));
        jarSizeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSizeFormatted()));
        jarDateColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDateModified()));
    }

    private void runTestBuild() {
        String buildFile = buildFilePathField.getText();
        String target = antTargetComboBox.getValue();
        String localJarPath = localJarPathField.getText();

        if (buildFile == null || buildFile.trim().isEmpty()) {
            CustomAlert.showError("Validation Error", "Please specify a build file.");
            return;
        }

        if (target == null || target.trim().isEmpty()) {
            CustomAlert.showError("Validation Error", "Please select a target.");
            return;
        }

        if (localJarPath == null || localJarPath.trim().isEmpty()) {
            CustomAlert.showError("Validation Error", "Please specify the JAR files directory to check results.");
            return;
        }

        testBuildBtn.setDisable(true);
        testBuildBtn.setText("⏳ Building...");

        Project tempProject = new Project();
        tempProject.setBuildFilePath(buildFile);
        tempProject.setAntTarget(target);
        tempProject.setAntCommand(antCommandArea.getText());

        Task<Boolean> buildTask = new Task<>() {
            @Override
            protected Boolean call() {
                BuildService buildService = new BuildService(tempProject, msg -> System.out.println(msg));
                var result = buildService.buildProject();
                return result.isSuccess();
            }
        };

        buildTask.setOnSucceeded(event -> {
            testBuildBtn.setDisable(false);
            testBuildBtn.setText("▶ Test Build");
            showBuildResults(buildTask.getValue(), localJarPath);
        });

        buildTask.setOnFailed(event -> {
            testBuildBtn.setDisable(false);
            testBuildBtn.setText("▶ Test Build");
            showBuildResults(false, localJarPath);
            CustomAlert.showError("Build Error", buildTask.getException().getMessage());
        });

        Thread thread = new Thread(buildTask, "Test-Build");
        thread.setDaemon(true);
        thread.start();
    }

    private void showBuildResults(boolean success, String jarDirectory) {
        List<JarFileInfo> jarFiles = scanForJarFiles(jarDirectory);

        if (success) {
            buildStatusLabel.setText("✓ Success");
            buildStatusLabel.getStyleClass().removeAll("build-status-error");
            if (!buildStatusLabel.getStyleClass().contains("build-status-success")) {
                buildStatusLabel.getStyleClass().add("build-status-success");
            }

            jarCountLabel.getStyleClass().removeAll("build-status-error");
            if (!jarCountLabel.getStyleClass().contains("build-status-success")) {
                jarCountLabel.getStyleClass().add("build-status-success");
            }
        } else {
            buildStatusLabel.setText("✗ Failed");
            buildStatusLabel.getStyleClass().removeAll("build-status-success");
            if (!buildStatusLabel.getStyleClass().contains("build-status-error")) {
                buildStatusLabel.getStyleClass().add("build-status-error");
            }

            jarCountLabel.getStyleClass().removeAll("build-status-success");
            if (!jarCountLabel.getStyleClass().contains("build-status-error")) {
                jarCountLabel.getStyleClass().add("build-status-error");
            }
        }

        jarCountLabel.setText(jarFiles.size() + " JARS Generated");

        jarResultsTable.getItems().clear();
        jarResultsTable.getItems().addAll(jarFiles);

        buildResultsContainer.setVisible(true);
        buildResultsContainer.setManaged(true);

        Platform.runLater(() -> mainScrollPane.setVvalue(1.0));
    }

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

    private String formatTotalSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TABLE & PROJECT MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    private void setupTable() {
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));
        projectsTable.setItems(projectsList);
    }

    private void loadProjects() {
        projectsList.clear();
        projectManager.reload();
        List<Project> projects = projectManager.getProjects();
        projectsList.addAll(projects);
        System.out.println("Loaded " + projects.size() + " projects into table");
    }

    private void loadProjectToFields(Project project) {
        nameField.setText(project.getName());
        localJarPathField.setText(project.getLocalJarPath() != null ? project.getLocalJarPath() : "");
        localJspPathField.setText(project.getLocalJspPath() != null ? project.getLocalJspPath() : "");
        remoteJarPathField.setText(project.getRemoteJarPath() != null ? project.getRemoteJarPath() : "");
        remoteJspPathField.setText(project.getRemoteJspPath() != null ? project.getRemoteJspPath() : "");
        buildFilePathField.setText(project.getBuildFilePath() != null ? project.getBuildFilePath() : "");

        updateAntTargets(project.getBuildFilePath());
        if (project.getAntTarget() != null && !project.getAntTarget().isEmpty()) {
            antTargetComboBox.setValue(project.getAntTarget());
        }

        loadLibrariesToUI(project.getAntLibraries());

        antCommandArea.setText(project.getAntCommand() != null ? project.getAntCommand() : "");
    }

    private void clearFields() {
        nameField.clear();
        localJarPathField.clear();
        localJspPathField.clear();
        remoteJarPathField.clear();
        remoteJspPathField.clear();
        buildFilePathField.clear();
        antTargetComboBox.getItems().clear();
        antTargetComboBox.setValue(null);
        clearLibraryRows();
        antCommandArea.clear();
        projectsTable.getSelectionModel().clearSelection();
        selectedProject = null;
        updateLibrariesVisibility();

        hideBuildResults();
    }

    private void addProject() {
        if (!validateFields()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields");
            return;
        }

        Project project = new Project();
        project.setName(nameField.getText().trim());
        project.setLocalJarPath(localJarPathField.getText().trim());
        project.setLocalJspPath(localJspPathField.getText().trim());
        project.setRemoteJarPath(remoteJarPathField.getText().trim());
        project.setRemoteJspPath(remoteJspPathField.getText().trim());
        project.setBuildFilePath(buildFilePathField.getText().trim());
        project.setAntTarget(antTargetComboBox.getValue());
        project.setAntCommand(antCommandArea.getText());
        project.setAntLibraries(getLibrariesFromUI());

        projectManager.addProject(project);
        loadProjects();
        clearFields();

        CustomAlert.showInfo("Success", "Project added successfully!");
    }

    private void updateProject() {
        if (selectedProject == null) {
            CustomAlert.showError("Selection Error", "Please select a project to update.");
            return;
        }

        if (!validateFields()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields.");
            return;
        }

        Project updatedProject = new Project();
        updatedProject.setName(nameField.getText().trim());
        updatedProject.setLocalJarPath(localJarPathField.getText().trim());
        updatedProject.setLocalJspPath(localJspPathField.getText().trim());
        updatedProject.setRemoteJarPath(remoteJarPathField.getText().trim());
        updatedProject.setRemoteJspPath(remoteJspPathField.getText().trim());
        updatedProject.setBuildFilePath(buildFilePathField.getText().trim());
        updatedProject.setAntTarget(antTargetComboBox.getValue());
        updatedProject.setAntCommand(antCommandArea.getText());
        updatedProject.setAntLibraries(getLibrariesFromUI());

        projectManager.updateProject(selectedProject, updatedProject);
        loadProjects();
        clearFields();

        CustomAlert.showInfo("Success", "Project updated successfully!");
    }

    private void deleteProject() {
        if (selectedProject == null) {
            CustomAlert.showError("Selection Error", "Please select a project to delete.");
            return;
        }

        boolean confirmed = CustomAlert.showConfirmation(
                this, "Delete Project",
                "Are you sure you want to delete the project '" + selectedProject.getName() + "'?"
        );

        if (confirmed) {
            projectManager.deleteProject(selectedProject);
            loadProjects();
            clearFields();
            CustomAlert.showInfo("Success", "Project deleted successfully!");
        }
    }

    private boolean validateFields() {
        return !nameField.getText().trim().isEmpty()
                && !localJarPathField.getText().trim().isEmpty()
                && !localJspPathField.getText().trim().isEmpty()
                && !remoteJarPathField.getText().trim().isEmpty()
                && !remoteJspPathField.getText().trim().isEmpty()
                && !buildFilePathField.getText().trim().isEmpty()
                && antTargetComboBox.getValue() != null
                && !antTargetComboBox.getValue().trim().isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE BROWSING
    // ═══════════════════════════════════════════════════════════════════════════

    private void browseForBuildFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Ant Build File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        String currentPath = buildFilePathField.getText();
        if (!currentPath.isEmpty()) {
            try {
                if (Files.exists(Paths.get(currentPath).getParent())) {
                    fileChooser.setInitialDirectory(Paths.get(currentPath).getParent().toFile());
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        File selectedFile = fileChooser.showOpenDialog(this);
        if (selectedFile != null) {
            buildFilePathField.setText(selectedFile.getAbsolutePath());
            updateAntTargets(selectedFile.getAbsolutePath());
        }
    }

    private void updateAntTargets(String buildFilePath) {
        antTargetComboBox.getItems().clear();
        antTargetComboBox.setValue(null);

        if (buildFilePath == null || buildFilePath.trim().isEmpty()) {
            return;
        }

        File file = new File(buildFilePath);
        if (!file.exists() || !file.isFile()) {
            return;
        }

        List<String> targets = AntFileParser.parseTargets(buildFilePath);

        if (!targets.isEmpty()) {
            antTargetComboBox.getItems().addAll(targets);

            String defaultTarget = AntFileParser.getDefaultTarget(buildFilePath);
            if (defaultTarget != null && targets.contains(defaultTarget)) {
                antTargetComboBox.setValue(defaultTarget);
            }
        } else {
            System.out.println("⚠ No targets found in: " + buildFilePath);
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
            } catch (Exception e) {
                // Ignore
            }
        }

        File selectedFolder = directoryChooser.showDialog(this);
        if (selectedFolder != null) {
            targetField.setText(selectedFolder.getAbsolutePath());
        }
    }

    @Override
    public List<HitSpot> getHitSpots() {
        HitSpot spot = HitSpot.builder()
                .window(this)
                .control(closeBtn)
                .close(true)
                .build();

        spot.hoveredProperty().addListener((obs, o, hovered) -> {
            if (hovered) {
                if (!spot.getControl().getStyleClass().contains("hit-close-btn")) {
                    spot.getControl().getStyleClass().add("hit-close-btn");
                }
            } else {
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