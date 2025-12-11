package com.autodeploy.ui;

import com.autodeploy.assets.Assets;
import com.autodeploy.config.ProjectManager;
import com.autodeploy.config.ServerManager;
import com.autodeploy.model.Project;
import com.autodeploy.model.Server;
import com.autodeploy.ui.dialogs.CustomAlert;
import com.autodeploy.ui.dialogs.ProjectManagementDialog;
import com.autodeploy.ui.dialogs.ServerManagementDialog;
import com.autodeploy.ui.dialogs.SettingsDialog;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage; // Added import
import xss.it.nfx.NfxStage;
import xss.it.nfx.WindowState;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SelectionWindow extends NfxStage implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(SelectionWindow.class.getName());

    // Constants
    public static final String MIN_SHAPE = "M1 7L1 8L14 8L14 7Z";
    public static final String MAX_SHAPE = "M2.5 2 A 0.50005 0.50005 0 0 0 2 2.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L13.5 14 A 0.50005 0.50005 0 0 0 14 13.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L2.5 2 z M 3 3L13 3L13 13L3 13L3 3 z";
    public static final String REST_SHAPE = "M4.5 2 A 0.50005 0.50005 0 0 0 4 2.5L4 4L2.5 4 A 0.50005 0.50005 0 0 0 2 4.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L11.5 14 A 0.50005 0.50005 0 0 0 12 13.5L12 12L13.5 12 A 0.50005 0.50005 0 0 0 14 11.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L4.5 2 z M 5 3L13 3L13 11L12 11L12 4.5 A 0.50005 0.50005 0 0 0 11.5 4L5 4L5 3 z M 3 5L11 5L11 13L3 13L3 5 z";
    public static final String CLOSE_SHAPE = "M3.726563 3.023438L3.023438 3.726563L7.292969 8L3.023438 12.269531L3.726563 12.980469L8 8.707031L12.269531 12.980469L12.980469 12.269531L8.707031 8L12.980469 3.726563L12.269531 3.023438L8 7.292969Z";
    private static final String WINDOW_TITLE = "La Pila & La Ciocan";
    private static final double TITLE_BAR_HEIGHT = 40.0;
    private static final String SERVER_PROMPT = "Select a server...";
    private static final String PROJECT_PROMPT = "Select a project...";

    // UI Controls
    @FXML private Button closeBtn, maxBtn, minBtn;
    @FXML private SVGPath maxShape;
    @FXML private ImageView iconView;
    @FXML private Label title;
    @FXML private ComboBox<String> serverComboBox, projectComboBox;
    @FXML private MFXButton manageProjectsBtn, manageServersBtn, settingsBtn, startDeployBtn;

    // Data Managers
    private final ServerManager serverManager;
    private final ProjectManager projectManager;

    // Dialog Instances (Singletons for this window)
    private SettingsDialog settingsDialog;
    private ProjectManagementDialog projectManagementDialog;
    private ServerManagementDialog serverManagementDialog;

    // =================================================================================================================
    // INITIALIZATION
    // =================================================================================================================

    public SelectionWindow() {
        super();
        this.projectManager = ProjectManager.getInstance();
        this.serverManager = ServerManager.getInstance();

        try {
            initializeWindow();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize SelectionWindow", e);
            throw new RuntimeException("Failed to initialize SelectionWindow", e);
        }
    }

    private void initializeWindow() throws IOException {
        getIcons().add(new Image(Assets.load("/logo.png").toExternalForm()));
        Parent parent = Assets.load("/fxml/selection-window.fxml", this);
        Scene scene = new Scene(parent);
        setScene(scene);
        setTitle(WINDOW_TITLE);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTitleBar();
        setupComboBoxes();
        setupButtons();
    }

    private void setupTitleBar() {
        getIcons().addListener((ListChangeListener<? super Image>) observable -> {
            if (!getIcons().isEmpty()) iconView.setImage(getIcons().get(0));
        });

        titleProperty().addListener((observable, oldValue, newValue) -> title.setText(newValue));

        setCloseControl(closeBtn);
        setMaxControl(maxBtn);
        setMinControl(minBtn);

        handleMaxStateChangeShape(getWindowState());
        windowStateProperty().addListener((obs, oldState, newState) -> handleMaxStateChangeShape(newState));
    }

    private void setupComboBoxes() {
        // Setup Server Combo
        serverComboBox.setPromptText(SERVER_PROMPT);
        serverComboBox.setMaxWidth(Double.MAX_VALUE);
        loadServersFromJson();

        // Setup Project Combo
        projectComboBox.setPromptText(PROJECT_PROMPT);
        projectComboBox.setMaxWidth(Double.MAX_VALUE);
        loadProjectsFromJson();

        // Add Listeners
        serverComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> updateStartButtonState());
        projectComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> updateStartButtonState());
    }

    private void setupButtons() {
        startDeployBtn.setOnAction(e -> handleStartDeploy());

        Optional.ofNullable(manageServersBtn).ifPresent(btn -> btn.setOnAction(e -> openServerManagementDialog()));
        Optional.ofNullable(manageProjectsBtn).ifPresent(btn -> btn.setOnAction(e -> openProjectManagementDialog()));
        Optional.ofNullable(settingsBtn).ifPresent(btn -> btn.setOnAction(e -> openSettingsDialog()));
    }

    // =================================================================================================================
    // ACTIONS
    // =================================================================================================================

    private void handleStartDeploy() {
        String serverStr = serverComboBox.getSelectionModel().getSelectedItem();
        String projectName = projectComboBox.getSelectionModel().getSelectedItem();

        Project project = projectManager.findProjectByName(projectName);
        Server server = findServerFromString(serverStr);

        if (project != null && server != null) {
            openDeploymentWindow(project, server);
        } else {
            LOGGER.warning("Invalid project or server selection");
            CustomAlert.showError("Selection Error", "Please select both a valid project and server.");
        }
    }

    private void openDeploymentWindow(Project project, Server server) {
        try {
            LOGGER.info("Opening deployment window with SFTP connection");

            DeploymentWindow deploymentWindow = new DeploymentWindow(project, server);
            deploymentWindow.setSelectionWindow(this);
            deploymentWindow.show();
            // Connect logic happens inside DeploymentWindow's init

            this.close();

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error opening deployment window", ex);
            CustomAlert.showError("Connection Error", "Failed to open deployment window:\n" + ex.getMessage());
        }
    }

    // =================================================================================================================
    // DIALOG MANAGEMENT
    // =================================================================================================================

    private void openServerManagementDialog() {
        if (isDialogAlreadyOpen(serverManagementDialog)) return;

        try {
            LOGGER.info("Opening Server Management Dialog");
            serverManagementDialog = new ServerManagementDialog(true);
            serverManagementDialog.initOwner(this);
            serverManagementDialog.centerOnScreen();
            serverManagementDialog.setOnHidden(event -> {
                refreshServerComboBox();
                serverManagementDialog = null;
                LOGGER.info("Server Management Dialog closed");
            });
            serverManagementDialog.show();
        } catch (Exception ex) {
            handleDialogError("Server Management", ex);
            serverManagementDialog = null;
        }
    }

    private void openProjectManagementDialog() {
        if (isDialogAlreadyOpen(projectManagementDialog)) return;

        try {
            LOGGER.info("Opening Project Management Dialog");
            projectManagementDialog = new ProjectManagementDialog(true);
            projectManagementDialog.initOwner(this);
            projectManagementDialog.centerOnScreen();
            projectManagementDialog.setOnHidden(event -> {
                refreshProjectComboBox();
                projectManagementDialog = null;
                LOGGER.info("Project Management Dialog closed");
            });
            projectManagementDialog.show();
        } catch (Exception ex) {
            handleDialogError("Project Management", ex);
            projectManagementDialog = null;
        }
    }

    private void openSettingsDialog() {
        if (isDialogAlreadyOpen(settingsDialog)) return;

        try {
            LOGGER.info("Opening Settings Dialog");
            settingsDialog = new SettingsDialog(true);
            settingsDialog.initOwner(this);
            settingsDialog.centerOnScreen();
            settingsDialog.setOnHidden(event -> {
                settingsDialog = null;
                LOGGER.info("Settings Dialog closed");
            });
            settingsDialog.show();
        } catch (Exception ex) {
            handleDialogError("Settings", ex);
            settingsDialog = null;
        }
    }

    private boolean isDialogAlreadyOpen(Stage dialog) {
        if (dialog != null && dialog.isShowing()) {
            dialog.toFront();
            dialog.requestFocus();
            LOGGER.info("Dialog already open, bringing to front");
            return true;
        }
        return false;
    }

    private void handleDialogError(String dialogName, Exception ex) {
        LOGGER.log(Level.SEVERE, "Error opening " + dialogName + " dialog", ex);
        CustomAlert.showError("Dialog Error", "Failed to open " + dialogName + " dialog:\n" + ex.getMessage());
    }

    // =================================================================================================================
    // HELPERS (Data Loading & UI Updates)
    // =================================================================================================================

    private void loadProjectsFromJson() {
        loadItemsIntoComboBox(projectComboBox, projectManager.getProjects(), Project::getName);
        LOGGER.info(String.format("Loaded %d projects", projectComboBox.getItems().size()));
    }

    private void loadServersFromJson() {
        loadItemsIntoComboBox(serverComboBox, serverManager.getServers(), Server::toString);
        LOGGER.info(String.format("Loaded %d servers", serverComboBox.getItems().size()));
    }

    private <T> void loadItemsIntoComboBox(ComboBox<String> comboBox, List<T> items, Function<T, String> toStringFunction) {
        comboBox.getItems().clear();
        items.stream().map(toStringFunction).forEach(comboBox.getItems()::add);
    }

    private void refreshComboBox(ComboBox<String> comboBox, Runnable loadMethod, String itemType) {
        String currentSelection = comboBox.getSelectionModel().getSelectedItem();
        loadMethod.run();

        if (currentSelection != null && comboBox.getItems().contains(currentSelection)) {
            comboBox.getSelectionModel().select(currentSelection);
        }
        LOGGER.info(String.format("Refreshed %s list", itemType));
    }

    private void refreshProjectComboBox() {
        refreshComboBox(projectComboBox, this::loadProjectsFromJson, "project");
    }

    private void refreshServerComboBox() {
        refreshComboBox(serverComboBox, this::loadServersFromJson, "server");
    }

    private void updateStartButtonState() {
        boolean bothSelected = serverComboBox.getSelectionModel().getSelectedItem() != null
                && projectComboBox.getSelectionModel().getSelectedItem() != null;
        startDeployBtn.setDisable(!bothSelected);
    }

    private Server findServerFromString(String serverStr) {
        return serverManager.getServers().stream()
                .filter(server -> server.toString().equals(serverStr))
                .findFirst()
                .orElse(null);
    }

    private void handleMaxStateChangeShape(WindowState state) {
        maxShape.setContent(Objects.equals(state, WindowState.MAXIMIZED) ? REST_SHAPE : MAX_SHAPE);
    }

    @Override
    protected double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }
}