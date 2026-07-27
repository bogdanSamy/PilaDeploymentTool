package com.autodeploy.ui.tab;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.domain.manager.ProjectManager;
import com.autodeploy.domain.manager.ServerManager;
import com.autodeploy.domain.model.Project;
import com.autodeploy.domain.model.Server;
import com.autodeploy.ui.dialog.CustomAlert;
import com.autodeploy.ui.dialog.ProjectManagementDialog;
import com.autodeploy.ui.dialog.ServerManagementDialog;
import com.autodeploy.ui.dialog.SettingsDialog;
import com.autodeploy.ui.window.component.DialogManager;
import com.autodeploy.ui.window.component.SelectionComboManager;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tab de selecție server/proiect — primul pas al unui nou deployment.
 * <p>
 * Conținutul este identic cu cel din {@link com.autodeploy.ui.window.SelectionWindow},
 * minus title bar-ul propriu (acum shared în {@link com.autodeploy.ui.window.MainWindow}).
 * <p>
 * La "Start Deploy": deschide un {@link DeploymentSessionTab} și se auto-închide.
 */
public class SelectionSessionTab extends SessionTab implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(SelectionSessionTab.class.getName());

    @FXML private ComboBox<Server> serverComboBox;
    @FXML private ComboBox<Project> projectComboBox;
    @FXML private MFXButton manageProjectsBtn;
    @FXML private MFXButton manageServersBtn;
    @FXML private MFXButton settingsBtn;
    @FXML private MFXButton startDeployBtn;

    private final SessionTabManager tabManager;
    private final Stage ownerStage;
    private DialogManager dialogManager;
    private SelectionComboManager comboManager;

    public SelectionSessionTab(SessionTabManager tabManager, Stage ownerStage) {
        this.tabManager = tabManager;
        this.ownerStage = ownerStage;
        setDisplayName("Select...");

        try {
            contentRoot = Assets.loadFxml("/fxml/selection-tab-content.fxml", this);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load selection tab content", e);
            throw new RuntimeException("Failed to load selection tab content", e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dialogManager = new DialogManager(ownerStage);

        comboManager = new SelectionComboManager(
                serverComboBox, projectComboBox,
                ServerManager.getInstance(),
                ProjectManager.getInstance()
        );
        comboManager.setOnSelectionChanged(this::updateStartButtonState);
        comboManager.setup();

        startDeployBtn.setDisable(true);
        startDeployBtn.setOnAction(e -> handleStartDeploy());
        manageServersBtn.setOnAction(e -> openServerManagement());
        manageProjectsBtn.setOnAction(e -> openProjectManagement());
        settingsBtn.setOnAction(e -> openSettings());
    }

    private void updateStartButtonState() {
        startDeployBtn.setDisable(!comboManager.isBothSelected());
    }

    private void handleStartDeploy() {
        Project project = comboManager.getSelectedProject();
        Server server = comboManager.getSelectedServer();

        if (project == null || server == null) {
            CustomAlert.showError("Selection Error",
                    "Please select both a valid project and server.");
            return;
        }

        tabManager.openDeploymentTab(project, server);
        tabManager.closeTab(this);
    }

    /** Dialoguri de management — refresh lista la închidere, singleton per tip. */
    private void openServerManagement() {
        dialogManager.openDialog(
                ServerManagementDialog.class,
                () -> new ServerManagementDialog(true),
                "Server Management",
                comboManager::refreshServers
        );
    }

    private void openProjectManagement() {
        dialogManager.openDialog(
                ProjectManagementDialog.class,
                () -> new ProjectManagementDialog(true),
                "Project Management",
                comboManager::refreshProjects
        );
    }

    private void openSettings() {
        dialogManager.openDialog(
                SettingsDialog.class,
                () -> new SettingsDialog(true),
                "Settings",
                null
        );
    }

    @Override
    public void close() {
        // Tab de selecție nu are conexiuni sau watchers de curățat
    }
}
