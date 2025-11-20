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
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
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
import xss.it.nfx.NfxStage;
import xss.it.nfx.WindowState;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class SelectionWindow extends NfxStage implements Initializable {

    private static final PseudoClass HT_MIN = PseudoClass.getPseudoClass("ht-min");
    private static final PseudoClass HT_MAX = PseudoClass.getPseudoClass("ht-max");
    private static final PseudoClass HT_CLOSE = PseudoClass.getPseudoClass("ht-close");

    @FXML
    private Button closeBtn;
    @FXML
    private Button maxBtn;
    @FXML
    private SVGPath maxShape;
    @FXML
    private Button minBtn;
    @FXML
    private ImageView iconView;
    @FXML
    private Label title;

    @FXML
    private ComboBox<String> serverComboBox;
    @FXML
    private ComboBox<String> projectComboBox;
    @FXML
    private MFXButton manageProjectsBtn;
    @FXML
    private MFXButton manageServersBtn;
    @FXML
    private MFXButton settingsBtn;
    @FXML
    private MFXButton startDeployBtn;

    // Keep reference to dialogs to prevent multiple instances
    private SettingsDialog settingsDialog;
    private ProjectManagementDialog projectManagementDialog;
    private ServerManagementDialog serverManagementDialog;

    // Manager instances
    private final ServerManager serverManager;
    private final ProjectManager projectManager;

    public SelectionWindow() {
        super();
        this.projectManager = ProjectManager.getInstance();
        this.serverManager = ServerManager.getInstance();
        try {
            getIcons().add(
                    new Image(Assets.load("/icon.png").toExternalForm())
            );

            Parent parent = Assets.load("/selection-window.fxml", this);
            Scene scene = new Scene(parent);
            setScene(scene);
            initializeStage();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeStage() {
        setTitle("Deploy Manager");
    }

    @Override
    protected double getTitleBarHeight() {
        return 40;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Title bar setup
        getIcons().addListener((ListChangeListener<? super Image>) observable -> {
            if (!getIcons().isEmpty()) {
                iconView.setImage(getIcons().get(0));
            }
        });

        titleProperty().addListener(observable -> {
            title.setText(getTitle());
        });

        setCloseControl(closeBtn);
        setMaxControl(maxBtn);
        setMinControl(minBtn);

        handleMaxStateChangeShape(getWindowState());
        windowStateProperty().addListener((obs, o, state) -> handleMaxStateChangeShape(state));

        // Populate ComboBoxes
        setupComboBoxes();

        // Setup button actions
        setupButtons();
    }

    private void setupComboBoxes() {
        // Server ComboBox - Load from JSON
        loadServersFromJson();
        serverComboBox.setPromptText("Select a server...");
        serverComboBox.setMaxWidth(Double.MAX_VALUE);

        // Project ComboBox - Load from JSON
        loadProjectsFromJson();
        projectComboBox.setPromptText("Select a project...");
        projectComboBox.setMaxWidth(Double.MAX_VALUE);

        // Listeners
        serverComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> updateStartButtonState());
        projectComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> updateStartButtonState());
    }

    /**
     * Load projects from JSON file into project ComboBox
     */
    private void loadProjectsFromJson() {
        projectComboBox.getItems().clear();
        projectManager.getProjects().forEach(project ->
                projectComboBox.getItems().add(project.getName())
        );
        System.out.println("✓ Loaded " + projectComboBox.getItems().size() + " projects into ComboBox");
    }

    /**
     * Load servers from JSON file into server ComboBox
     */
    private void loadServersFromJson() {
        serverComboBox.getItems().clear();
        serverManager.getServers().forEach(server ->
                serverComboBox.getItems().add(server.toString())
        );
        System.out.println("✓ Loaded " + serverComboBox.getItems().size() + " servers into ComboBox");
    }

    /**
     * Refresh project ComboBox after changes
     */
    private void refreshProjectComboBox() {
        String currentSelection = projectComboBox.getSelectionModel().getSelectedItem();
        loadProjectsFromJson();
        if (currentSelection != null && projectComboBox.getItems().contains(currentSelection)) {
            projectComboBox.getSelectionModel().select(currentSelection);
        }
        System.out.println("↻ Refreshed project list");
    }

    /**
     * Refresh server ComboBox after changes
     */
    private void refreshServerComboBox() {
        String currentSelection = serverComboBox.getSelectionModel().getSelectedItem();
        loadServersFromJson();
        if (currentSelection != null && serverComboBox.getItems().contains(currentSelection)) {
            serverComboBox.getSelectionModel().select(currentSelection);
        }
        System.out.println("↻ Refreshed server list");
    }

    private void updateStartButtonState() {
        boolean bothSelected = serverComboBox.getSelectionModel().getSelectedItem() != null
                && projectComboBox.getSelectionModel().getSelectedItem() != null;
        startDeployBtn.setDisable(!bothSelected);
    }

    private void setupButtons() {
        // Start Deploy button - Opens DeploymentWindow
        startDeployBtn.setOnAction(e -> {
            String serverStr = serverComboBox.getSelectionModel().getSelectedItem();
            String projectName = projectComboBox.getSelectionModel().getSelectedItem();

            Project project = projectManager.findProjectByName(projectName);
            Server server = findServerFromString(serverStr);

            if (project != null && server != null) {
                openDeploymentWindow(project, server);
            } else {
                System.err.println("✗ Invalid project or server selection");
            }
        });

        // Manage Servers button - Opens ServerManagementDialog
        if (manageServersBtn != null) {
            manageServersBtn.setOnAction(e -> {
                openServerManagementDialog();
            });
        }

        // Manage Projects button - Opens ProjectManagementDialog
        if (manageProjectsBtn != null) {
            manageProjectsBtn.setOnAction(e -> {
                openProjectManagementDialog();
            });
        }

        // Settings button - Opens SettingsDialog
        if (settingsBtn != null) {
            settingsBtn.setOnAction(e -> {
                openSettingsDialog();
            });
        }
    }

    /**
     * Extract Server from ComboBox string
     */
    private Server findServerFromString(String serverStr) {
        // Format: "ServerName (192.168.1.100)"
        for (Server server : serverManager.getServers()) {
            if (server.toString().equals(serverStr)) {
                return server;
            }
        }
        return null;
    }

    /**
     * Opens the Server Management Dialog window (MODAL).
     */
    private void openServerManagementDialog() {
        try {
            if (serverManagementDialog != null && serverManagementDialog.isShowing()) {
                serverManagementDialog.toFront();
                serverManagementDialog.requestFocus();
                return;
            }

            System.out.println("Opening Server Management Dialog...");

            serverManagementDialog = new ServerManagementDialog(true);
            serverManagementDialog.initOwner(this);
            serverManagementDialog.centerOnScreen();
            serverManagementDialog.showAndWait();

            refreshServerComboBox();
            serverManagementDialog = null;

            System.out.println("✓ Server Management Dialog closed");

        } catch (Exception ex) {
            System.err.println("✗ Error opening server management dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Opens the Project Management Dialog window (MODAL).
     */
    private void openProjectManagementDialog() {
        try {
            if (projectManagementDialog != null && projectManagementDialog.isShowing()) {
                projectManagementDialog.toFront();
                projectManagementDialog.requestFocus();
                return;
            }

            System.out.println("Opening Project Management Dialog...");

            projectManagementDialog = new ProjectManagementDialog(true);
            projectManagementDialog.initOwner(this);
            projectManagementDialog.centerOnScreen();
            projectManagementDialog.showAndWait();

            refreshProjectComboBox();
            projectManagementDialog = null;

            System.out.println("✓ Project Management Dialog closed");

        } catch (Exception ex) {
            System.err.println("✗ Error opening project management dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Opens the Settings Dialog window (MODAL).
     */
    private void openSettingsDialog() {
        try {
            if (settingsDialog != null && settingsDialog.isShowing()) {
                settingsDialog.toFront();
                settingsDialog.requestFocus();
                return;
            }

            System.out.println("Opening Settings Dialog...");

            settingsDialog = new SettingsDialog(true);
            settingsDialog.initOwner(this);
            settingsDialog.centerOnScreen();
            settingsDialog.showAndWait();

            settingsDialog = null;

            System.out.println("✓ Settings Dialog closed");

        } catch (Exception ex) {
            System.err.println("✗ Error opening settings dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Open Deployment Window with SFTP connection and close Selection Window
     */
    private void openDeploymentWindow(Project project, Server server) {
        try {
            System.out.println("✓ Opening deployment window with SFTP connection...");

            // Create deployment window
            DeploymentWindow deploymentWindow = new DeploymentWindow(project, server);

            // Pass reference to Selection Window for "Change" button
            deploymentWindow.setSelectionWindow(this);

            // Show window (blur is already visible from constructor)
            deploymentWindow.show();

            // Start SFTP connection in background
            deploymentWindow.connectToServer();

            // Close Selection Window AFTER deployment window is shown
            this.close();

        } catch (Exception ex) {
            System.err.println("✗ Error opening deployment window: " + ex.getMessage());
            ex.printStackTrace();
            CustomAlert.showError("Connection Error", "Failed to open deployment window:\n" + ex.getMessage());
        }
    }

    /**
     * Get the currently selected project
     */
    public Project getSelectedProject() {
        String projectName = projectComboBox.getSelectionModel().getSelectedItem();
        if (projectName != null) {
            return projectManager.findProjectByName(projectName);
        }
        return null;
    }

    /**
     * Get the currently selected server
     */
    public String getSelectedServer() {
        return serverComboBox.getSelectionModel().getSelectedItem();
    }

    private void handleMaxStateChangeShape(WindowState state) {
        if (Objects.equals(state, WindowState.MAXIMIZED)) {
            maxShape.setContent(REST_SHAPE);
        } else {
            maxShape.setContent(MAX_SHAPE);
        }
    }

    public static final String MIN_SHAPE = "M1 7L1 8L14 8L14 7Z";
    public static final String MAX_SHAPE = "M2.5 2 A 0.50005 0.50005 0 0 0 2 2.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L13.5 14 A 0.50005 0.50005 0 0 0 14 13.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L2.5 2 z M 3 3L13 3L13 13L3 13L3 3 z";
    public static final String REST_SHAPE = "M4.5 2 A 0.50005 0.50005 0 0 0 4 2.5L4 4L2.5 4 A 0.50005 0.50005 0 0 0 2 4.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L11.5 14 A 0.50005 0.50005 0 0 0 12 13.5L12 12L13.5 12 A 0.50005 0.50005 0 0 0 14 11.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L4.5 2 z M 5 3L13 3L13 11L12 11L12 4.5 A 0.50005 0.50005 0 0 0 11.5 4L5 4L5 3 z M 3 5L11 5L11 13L3 13L3 5 z";
    public static final String CLOSE_SHAPE = "M3.726563 3.023438L3.023438 3.726563L7.292969 8L3.023438 12.269531L3.726563 12.980469L8 8.707031L12.269531 12.980469L12.980469 12.269531L8.707031 8L12.980469 3.726563L12.269531 3.023438L8 7.292969Z";
}