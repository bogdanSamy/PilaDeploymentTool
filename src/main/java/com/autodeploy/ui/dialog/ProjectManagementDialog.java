package com.autodeploy.ui.dialog;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.domain.manager.ProjectManager;
import com.autodeploy.domain.model.Project;
import com.autodeploy.service.deploy.BuildService;
import com.autodeploy.ui.dialog.helper.WindowDecorationHelper;
import com.autodeploy.ui.dialog.component.BuildResultsPanel;
import com.autodeploy.ui.dialog.component.LibraryRowManager;
import com.autodeploy.ui.dialog.component.ProjectFormBinder;
import com.autodeploy.ui.dialog.helper.FileBrowserHelper;
import com.autodeploy.ui.dialog.helper.JarFileInfo;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Dialog CRUD pentru managementul proiectelor.
 * <p>
 * Funcționalitate:
 * <ul>
 *   <li>Adăugare/editare/ștergere proiecte (persistate prin {@link ProjectManager})</li>
 *   <li>Configurare căi locale și remote (JAR/JSP)</li>
 *   <li>Selectare build file Ant — parsează automat target-urile disponibile</li>
 *   <li>Management librării Ant (adăugare/ștergere dinamică de rânduri)</li>
 *   <li>Test build — execută build-ul și afișează rezultatele (JAR-uri generate)</li>
 * </ul>
 * <p>
 * Componentele complexe sunt delegate către clase separate:
 * <ul>
 *   <li>{@link ProjectFormBinder} — binding form ↔ model, validare, generare comandă Ant</li>
 *   <li>{@link LibraryRowManager} — UI dinamic pentru lista de librării</li>
 *   <li>{@link BuildResultsPanel} — afișare rezultate test build</li>
 * </ul>
 */
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

    @FXML private HBox librariesHeader;
    @FXML private Button addLibraryInlineBtn;
    @FXML private VBox librariesContainer;
    @FXML private HBox librariesFooter;
    @FXML private Button addLibraryBtn;

    @FXML private TextArea antCommandArea;
    @FXML private Button regenerateCommandBtn;
    @FXML private Button testBuildBtn;

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

    private final ProjectManager projectManager;
    private final ObservableList<Project> projectsList;

    private ProjectFormBinder formBinder;
    private LibraryRowManager libraryRowManager;
    private BuildResultsPanel buildResultsPanel;

    private Project selectedProject;

    public ProjectManagementDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.projectManager = ProjectManager.getInstance();
        this.projectsList = FXCollections.observableArrayList();
        try {
            Parent parent = Assets.loadFxml("/fxml/project-management.fxml", this);
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
        setupTable();
        buildResultsPanel.setupTable();
        loadProjects();
        bindActions();
        bindListeners();

        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);
        libraryRowManager.updateVisibility();
    }

    private void initComponents() {
        libraryRowManager = new LibraryRowManager(
                librariesContainer, librariesFooter, addLibraryInlineBtn,
                mainScrollPane, this, () -> formBinder.generateCommand()
        );

        formBinder = new ProjectFormBinder(
                nameField, localJarPathField, localJspPathField,
                remoteJarPathField, remoteJspPathField, buildFilePathField,
                antTargetComboBox, antCommandArea, libraryRowManager
        );

        buildResultsPanel = new BuildResultsPanel(
                buildResultsContainer, buildStatusLabel, jarCountLabel,
                jarResultsTable, jarNameColumn, jarSizeColumn, jarDateColumn,
                mainScrollPane
        );
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getName()));
        projectsTable.setItems(projectsList);
    }

    private void bindActions() {
        closeBtn.setOnAction(e -> close());
        closeDialogBtn.setOnAction(e -> close());

        browseLocalJarBtn.setOnAction(e ->
                FileBrowserHelper.browseForFolder(this, localJarPathField, "Select Local JAR Directory"));
        browseLocalJspBtn.setOnAction(e ->
                FileBrowserHelper.browseForFolder(this, localJspPathField, "Select Local JSP Directory"));
        browseBuildFileBtn.setOnAction(e -> handleBrowseBuildFile());

        addLibraryInlineBtn.setOnAction(e -> {
            libraryRowManager.addRow("");
            libraryRowManager.updateVisibility();
        });
        addLibraryBtn.setOnAction(e -> {
            libraryRowManager.addRow("");
            libraryRowManager.updateVisibility();
        });

        regenerateCommandBtn.setOnAction(e -> formBinder.generateCommand());
        testBuildBtn.setOnAction(e -> runTestBuild());
        hideBuildResultsBtn.setOnAction(e -> buildResultsPanel.hide());

        addBtn.setOnAction(e -> addProject());
        updateBtn.setOnAction(e -> updateProject());
        deleteBtn.setOnAction(e -> deleteProject());
        clearBtn.setOnAction(e -> clearForm());
    }

    /**
     * Listeners reactivi:
     * <ul>
     *   <li>Selecție în tabel → populează form-ul și activează Update/Delete</li>
     *   <li>Build file pierde focus → parsează target-urile Ant</li>
     *   <li>Schimbări build file/target → regenerează comanda Ant automat</li>
     *   <li>Schimbare comandă → ascunde rezultatele build-ului anterior (invalidate)</li>
     * </ul>
     */
    private void bindListeners() {
        projectsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                formBinder.loadProject(newSel);
                selectedProject = newSel;
                updateBtn.setDisable(false);
                deleteBtn.setDisable(false);
            } else {
                selectedProject = null;
                updateBtn.setDisable(true);
                deleteBtn.setDisable(true);
            }
            buildResultsPanel.hide();
        });

        buildFilePathField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                formBinder.updateAntTargets(buildFilePathField.getText());
            }
        });

        buildFilePathField.textProperty().addListener((obs, old, newVal) -> formBinder.generateCommand());
        antTargetComboBox.valueProperty().addListener((obs, old, newVal) -> formBinder.generateCommand());

        antCommandArea.textProperty().addListener((obs, old, newVal) -> buildResultsPanel.hide());
    }

    private void handleBrowseBuildFile() {
        File selectedFile = FileBrowserHelper.browseForBuildFile(this, buildFilePathField.getText());
        if (selectedFile != null) {
            buildFilePathField.setText(selectedFile.getAbsolutePath());
            formBinder.updateAntTargets(selectedFile.getAbsolutePath());
        }
    }

    private void addProject() {
        if (!formBinder.isValid()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields.");
            return;
        }

        projectManager.addProject(formBinder.buildProjectFromFields());
        loadProjects();
        clearForm();
        CustomAlert.showInfo("Success", "Project added successfully!");
    }

    private void updateProject() {
        if (selectedProject == null) {
            CustomAlert.showError("Selection Error", "Please select a project to update.");
            return;
        }
        if (!formBinder.isValid()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields.");
            return;
        }

        projectManager.updateProject(selectedProject, formBinder.buildProjectFromFields());
        loadProjects();
        clearForm();
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
            clearForm();
            CustomAlert.showInfo("Success", "Project deleted successfully!");
        }
    }

    private void clearForm() {
        formBinder.clearAll();
        projectsTable.getSelectionModel().clearSelection();
        selectedProject = null;
        buildResultsPanel.hide();
    }

    private void loadProjects() {
        projectsList.clear();
        projectManager.reload();
        List<Project> projects = projectManager.getProjects();
        projectsList.addAll(projects);
    }

    /**
     * Validează, dezactivează butonul, și lansează build-ul pe un thread daemon.
     * Rezultatul (succes/fail + lista JAR-urilor generate) e afișat în BuildResultsPanel.
     */
    private void runTestBuild() {
        String buildFile = formBinder.getBuildFilePath();
        String target = formBinder.getAntTarget();
        String localJarPath = formBinder.getLocalJarPath();

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

        Thread thread = runTestBuild(buildFile, target, localJarPath);
        thread.start();
    }

    /**
     * Creează un Project temporar (nu persistat) doar pentru a rula build-ul de test.
     * Output-ul build-ului merge la System.out (nu la log panel-ul principal).
     */
    private Thread runTestBuild(String buildFile, String target, String localJarPath) {
        Project tempProject = new Project();
        tempProject.setBuildFilePath(buildFile);
        tempProject.setAntTarget(target);
        tempProject.setAntCommand(formBinder.getAntCommand());

        Task<Boolean> buildTask = new Task<>() {
            @Override
            protected Boolean call() {
                BuildService buildService = new BuildService(tempProject, System.out::println);
                return buildService.buildProject().isSuccess();
            }
        };

        buildTask.setOnSucceeded(event -> {
            resetTestBuildButton();
            buildResultsPanel.showResults(buildTask.getValue(), localJarPath);
        });

        buildTask.setOnFailed(event -> {
            resetTestBuildButton();
            buildResultsPanel.showResults(false, localJarPath);
            CustomAlert.showError("Build Error", buildTask.getException().getMessage());
        });

        Thread thread = new Thread(buildTask, "Test-Build");
        thread.setDaemon(true);
        return thread;
    }

    private void resetTestBuildButton() {
        testBuildBtn.setDisable(false);
        testBuildBtn.setText("▶ Test Build");
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