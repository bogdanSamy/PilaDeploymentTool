package com.autodeploy.ui.dialog.component;

import com.autodeploy.domain.model.Project;
import com.autodeploy.service.utility.AntFileParser;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.File;
import java.util.List;

/**
 * Binding bidirecțional între formularul de proiect și modelul {@link Project}.
 * <p>
 * Responsabilități:
 * <ul>
 *   <li>Populare form din model ({@link #loadProject})</li>
 *   <li>Construire model din form ({@link #buildProjectFromFields})</li>
 *   <li>Validare form ({@link #isValid})</li>
 *   <li>Parsare target-uri Ant din build file ({@link #updateAntTargets})</li>
 *   <li>Generare comandă Ant ({@link #generateCommand})</li>
 * </ul>
 */
public class ProjectFormBinder {

    private final TextField nameField;
    private final TextField localJarPathField;
    private final TextField localJspPathField;
    private final TextField remoteJarPathField;
    private final TextField remoteJspPathField;
    private final TextField buildFilePathField;
    private final ComboBox<String> antTargetComboBox;
    private final TextArea antCommandArea;
    private final LibraryRowManager libraryRowManager;

    public ProjectFormBinder(TextField nameField, TextField localJarPathField,
                             TextField localJspPathField, TextField remoteJarPathField,
                             TextField remoteJspPathField, TextField buildFilePathField,
                             ComboBox<String> antTargetComboBox, TextArea antCommandArea,
                             LibraryRowManager libraryRowManager) {
        this.nameField = nameField;
        this.localJarPathField = localJarPathField;
        this.localJspPathField = localJspPathField;
        this.remoteJarPathField = remoteJarPathField;
        this.remoteJspPathField = remoteJspPathField;
        this.buildFilePathField = buildFilePathField;
        this.antTargetComboBox = antTargetComboBox;
        this.antCommandArea = antCommandArea;
        this.libraryRowManager = libraryRowManager;
    }

    /**
     * Populează toate câmpurile din model.
     * Ordinea contează: build file → targets → selectare target → librării → comandă.
     * Target-ul trebuie setat DUPĂ parsarea target-urilor disponibile.
     */
    public void loadProject(Project project) {
        nameField.setText(project.getName());
        localJarPathField.setText(nullSafe(project.getLocalJarPath()));
        localJspPathField.setText(nullSafe(project.getLocalJspPath()));
        remoteJarPathField.setText(nullSafe(project.getRemoteJarPath()));
        remoteJspPathField.setText(nullSafe(project.getRemoteJspPath()));
        buildFilePathField.setText(nullSafe(project.getBuildFilePath()));

        updateAntTargets(project.getBuildFilePath());
        if (project.getAntTarget() != null && !project.getAntTarget().isEmpty()) {
            antTargetComboBox.setValue(project.getAntTarget());
        }

        libraryRowManager.loadLibraries(project.getAntLibraries());
        antCommandArea.setText(nullSafe(project.getAntCommand()));
    }

    public Project buildProjectFromFields() {
        Project project = new Project();
        project.setName(nameField.getText().trim());
        project.setLocalJarPath(localJarPathField.getText().trim());
        project.setLocalJspPath(localJspPathField.getText().trim());
        project.setRemoteJarPath(remoteJarPathField.getText().trim());
        project.setRemoteJspPath(remoteJspPathField.getText().trim());
        project.setBuildFilePath(buildFilePathField.getText().trim());
        project.setAntTarget(antTargetComboBox.getValue());
        project.setAntCommand(antCommandArea.getText());
        project.setAntLibraries(libraryRowManager.getLibraryPaths());
        return project;
    }

    public void clearAll() {
        nameField.clear();
        localJarPathField.clear();
        localJspPathField.clear();
        remoteJarPathField.clear();
        remoteJspPathField.clear();
        buildFilePathField.clear();
        antTargetComboBox.getItems().clear();
        antTargetComboBox.setValue(null);
        libraryRowManager.clearAll();
        antCommandArea.clear();
        libraryRowManager.updateVisibility();
    }

    public boolean isValid() {
        return !nameField.getText().trim().isEmpty()
                && !localJarPathField.getText().trim().isEmpty()
                && !localJspPathField.getText().trim().isEmpty()
                && !remoteJarPathField.getText().trim().isEmpty()
                && !remoteJspPathField.getText().trim().isEmpty()
                && !buildFilePathField.getText().trim().isEmpty()
                && antTargetComboBox.getValue() != null
                && !antTargetComboBox.getValue().trim().isEmpty();
    }

    /**
     * Parsează build.xml cu {@link AntFileParser}, populează dropdown-ul de targets,
     * și pre-selectează target-ul default (atributul "default" din tag-ul {@code <project>}).
     */
    public void updateAntTargets(String buildFilePath) {
        antTargetComboBox.getItems().clear();
        antTargetComboBox.setValue(null);

        if (buildFilePath == null || buildFilePath.trim().isEmpty()) return;

        File file = new File(buildFilePath);
        if (!file.exists() || !file.isFile()) return;

        List<String> targets = AntFileParser.parseTargets(buildFilePath);
        if (!targets.isEmpty()) {
            antTargetComboBox.getItems().addAll(targets);
            String defaultTarget = AntFileParser.getDefaultTarget(buildFilePath);
            if (defaultTarget != null && targets.contains(defaultTarget)) {
                antTargetComboBox.setValue(defaultTarget);
            }
        }
    }

    /**
     * Generează comanda Ant din componentele formularului.
     * Format: {@code ant [-lib "path"] [-f "build.xml"] [target]}
     */
    public void generateCommand() {
        StringBuilder command = new StringBuilder("ant");

        for (String lib : libraryRowManager.getLibraryPaths()) {
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

    public String getBuildFilePath() { return buildFilePathField.getText(); }
    public String getAntTarget() { return antTargetComboBox.getValue(); }
    public String getLocalJarPath() { return localJarPathField.getText(); }
    public String getAntCommand() { return antCommandArea.getText(); }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}