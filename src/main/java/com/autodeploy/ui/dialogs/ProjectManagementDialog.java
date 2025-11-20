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
import com.autodeploy.config.ProjectManager;
import com.autodeploy.model.Project;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class ProjectManagementDialog extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML
    private Button closeBtn;

    @FXML
    private TableView<Project> projectsTable;

    @FXML
    private TableColumn<Project, String> nameColumn;

    @FXML
    private TableColumn<Project, String> localJarColumn;

    @FXML
    private TableColumn<Project, String> remoteJarColumn;

    @FXML
    private TextField nameField;

    @FXML
    private TextField localJarPathField;

    @FXML
    private TextField localJspPathField;

    @FXML
    private TextField remoteJarPathField;

    @FXML
    private TextField remoteJspPathField;

    @FXML
    private TextField buildFilePathField;

    @FXML
    private Button browseLocalJarBtn;

    @FXML
    private Button browseLocalJspBtn;

    @FXML
    private Button browseBuildFileBtn;

    @FXML
    private Button addBtn;

    @FXML
    private Button updateBtn;

    @FXML
    private Button deleteBtn;

    @FXML
    private Button clearBtn;

    @FXML
    private Button closeDialogBtn;

    /**
     * The height of the title bar.
     */
    private static final int TITLE_BAR_HEIGHT = 30;

    /**
     * Project manager instance
     */
    private final ProjectManager projectManager;

    /**
     * Observable list of projects
     */
    private final ObservableList<Project> projectsList;

    /**
     * Currently selected project for editing
     */
    private Project selectedProject;

    /**
     * Constructs a new instance of ProjectManagementDialog with an option to hide from the taskbar.
     *
     * @param hideFromTaskBar Indicates whether the dialog should be hidden from the taskbar.
     */
    public ProjectManagementDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.projectManager = ProjectManager.getInstance();
        this.projectsList = FXCollections.observableArrayList();
        try {
            Parent parent = Assets.load("/project-management.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            setResizable(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Setup table columns
        setupTable();

        // Load projects - IMPORTANT: Load after table setup
        loadProjects();

        // Close button
        closeBtn.setOnAction(event -> close());

        // Close dialog button
        closeDialogBtn.setOnAction(event -> close());

        // Browse buttons
        browseLocalJarBtn.setOnAction(event -> browseForFolder(localJarPathField, "Select Local JAR Directory"));
        browseLocalJspBtn.setOnAction(event -> browseForFolder(localJspPathField, "Select Local JSP Directory"));
        browseBuildFileBtn.setOnAction(event -> browseForFile(buildFilePathField, "Select Build File"));

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

        // Initially disable update/delete buttons
        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);

        // Debug info
        System.out.println("ProjectManagementDialog initialized with " + projectsList.size() + " projects");
    }

    /**
     * Setup table columns
     */
    private void setupTable() {
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));

        localJarColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getLocalJarPath()));

        remoteJarColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRemoteJarPath()));

        projectsTable.setItems(projectsList);
    }

    /**
     * Load projects from ProjectManager
     */
    /**
     * Load projects from ProjectManager
     */
    private void loadProjects() {
        projectsList.clear();

        // Reload from file to ensure we have the latest data
        projectManager.reload();

        List<Project> projects = projectManager.getProjects();
        projectsList.addAll(projects);

        System.out.println("Loaded " + projects.size() + " projects into table");
    }

    /**
     * Load project data into form fields
     */
    private void loadProjectToFields(Project project) {
        nameField.setText(project.getName());
        localJarPathField.setText(project.getLocalJarPath() != null ? project.getLocalJarPath() : "");
        localJspPathField.setText(project.getLocalJspPath() != null ? project.getLocalJspPath() : "");
        remoteJarPathField.setText(project.getRemoteJarPath() != null ? project.getRemoteJarPath() : "");
        remoteJspPathField.setText(project.getRemoteJspPath() != null ? project.getRemoteJspPath() : "");
        buildFilePathField.setText(project.getBuildFilePath() != null ? project.getBuildFilePath() : "");
    }

    /**
     * Clear all form fields
     */
    private void clearFields() {
        nameField.clear();
        localJarPathField.clear();
        localJspPathField.clear();
        remoteJarPathField.clear();
        remoteJspPathField.clear();
        buildFilePathField.clear();
        projectsTable.getSelectionModel().clearSelection();
        selectedProject = null;
    }

    /**
     * Add new project
     */
    private void addProject() {
        if (!validateFields()) {
            showAlert("Validation Error", "Please fill in all required fields:\n- Name\n- Local JAR Path\n- Remote JAR Path");
            return;
        }

        Project project = new Project();
        project.setName(nameField.getText().trim());
        project.setLocalJarPath(localJarPathField.getText().trim());
        project.setLocalJspPath(localJspPathField.getText().trim());
        project.setRemoteJarPath(remoteJarPathField.getText().trim());
        project.setRemoteJspPath(remoteJspPathField.getText().trim());
        project.setBuildFilePath(buildFilePathField.getText().trim());

        projectManager.addProject(project);
        loadProjects();
        clearFields();

        showInfo("Success", "Project added successfully!");
    }

    /**
     * Update existing project
     */
    private void updateProject() {
        if (selectedProject == null) {
            showAlert("Selection Error", "Please select a project to update.");
            return;
        }

        if (!validateFields()) {
            showAlert("Validation Error", "Please fill in all required fields:\n- Name\n- Local JAR Path\n- Remote JAR Path");
            return;
        }

        Project updatedProject = new Project();
        updatedProject.setName(nameField.getText().trim());
        updatedProject.setLocalJarPath(localJarPathField.getText().trim());
        updatedProject.setLocalJspPath(localJspPathField.getText().trim());
        updatedProject.setRemoteJarPath(remoteJarPathField.getText().trim());
        updatedProject.setRemoteJspPath(remoteJspPathField.getText().trim());
        updatedProject.setBuildFilePath(buildFilePathField.getText().trim());

        projectManager.updateProject(selectedProject, updatedProject);
        loadProjects();
        clearFields();

        showInfo("Success", "Project updated successfully!");
    }

    /**
     * Delete selected project
     */
    private void deleteProject() {
        if (selectedProject == null) {
            showAlert("Selection Error", "Please select a project to delete.");
            return;
        }

        boolean confirmed = showConfirmation(
                "Delete Project",
                "Are you sure you want to delete the project '" + selectedProject.getName() + "'?"
        );

        if (confirmed) {
            projectManager.deleteProject(selectedProject);
            loadProjects();
            clearFields();
            showInfo("Success", "Project deleted successfully!");
        }
    }

    /**
     * Validate required fields
     */
    private boolean validateFields() {
        return !nameField.getText().trim().isEmpty()
                && !localJarPathField.getText().trim().isEmpty()
                && !remoteJarPathField.getText().trim().isEmpty();
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
     * Show error alert
     */
    private void showAlert(String title, String message) {
        CustomAlert.showError(title, message);
    }

    /**
     * Show info alert
     */
    private void showInfo(String title, String message) {
        CustomAlert.showInfo(title, message);
    }

    /**
     * Show confirmation dialog
     */
    private boolean showConfirmation(String title, String message) {
        return CustomAlert.showConfirmation(title, message);
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