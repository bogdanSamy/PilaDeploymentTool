package com.autodeploy.ui.window;

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
import xss.it.nfx.NfxStage;
import xss.it.nfx.WindowState;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Fereastra de selecție — primul ecran al aplicației.
 * User-ul alege un server și un proiect, apoi apasă "Start Deploy".
 * <p>
 * Funcționalitate:
 * <ul>
 *   <li>Selecție server + proiect prin combo box-uri (obiecte domeniu, nu String-uri)</li>
 *   <li>Butonul "Start Deploy" e disabled până când ambele selecții sunt făcute</li>
 *   <li>Deschidere dialoguri de management (servere, proiecte, setări) cu refresh
 *       automat al listelor la închidere</li>
 * </ul>
 * <p>
 * Navigare: SelectionWindow → {@link DeploymentWindow} (această fereastră se ascunde,
 * nu se distruge — DeploymentWindow o poate re-afișa la "Change Project/Server").
 */
public class SelectionWindow extends NfxStage implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(SelectionWindow.class.getName());

    @FXML private Button closeBtn;
    @FXML private Button maxBtn;
    @FXML private Button minBtn;
    @FXML private SVGPath maxShape;
    @FXML private ImageView iconView;
    @FXML private Label title;
    @FXML private ComboBox<Server> serverComboBox;
    @FXML private ComboBox<Project> projectComboBox;
    @FXML private MFXButton manageProjectsBtn;
    @FXML private MFXButton manageServersBtn;
    @FXML private MFXButton settingsBtn;
    @FXML private MFXButton startDeployBtn;

    private DialogManager dialogManager;
    private SelectionComboManager comboManager;

    public SelectionWindow() {
        super();
        try {
            initializeWindow();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize SelectionWindow", e);
            throw new RuntimeException("Failed to initialize SelectionWindow", e);
        }
    }

    private void initializeWindow() throws IOException {
        getIcons().add(new Image(Assets.location("/logo.png").toExternalForm()));
        Parent parent = Assets.loadFxml("/fxml/selection-window.fxml", this);
        setScene(new Scene(parent));
        setTitle(WINDOW_TITLE);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initComponents();
        setupTitleBar();
        comboManager.setup();
        bindActions();
    }

    private void initComponents() {
        dialogManager = new DialogManager(this);

        comboManager = new SelectionComboManager(
                serverComboBox, projectComboBox,
                ServerManager.getInstance(),
                ProjectManager.getInstance()
        );

        comboManager.setOnSelectionChanged(this::updateStartButtonState);
    }

    /**
     * Title bar inline (nu folosește TitleBarManager deoarece SelectionWindow
     * e mai simplă — nu necesită reutilizare a pattern-ului).
     */
    private void setupTitleBar() {
        getIcons().addListener((ListChangeListener<? super Image>) observable -> {
            if (!getIcons().isEmpty()) {
                iconView.setImage(getIcons().getFirst());
            }
        });

        titleProperty().addListener((obs, oldVal, newVal) -> title.setText(newVal));

        setCloseControl(closeBtn);
        setMaxControl(maxBtn);
        setMinControl(minBtn);

        updateMaxShape(getWindowState());
        windowStateProperty().addListener((obs, oldState, newState) -> updateMaxShape(newState));
    }

    private void updateMaxShape(WindowState state) {
        maxShape.setContent(Objects.equals(state, WindowState.MAXIMIZED) ? REST_SHAPE : MAX_SHAPE);
    }

    private void bindActions() {
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
            LOGGER.warning("Invalid project or server selection");
            CustomAlert.showError("Selection Error",
                    "Please select both a valid project and server.");
            return;
        }

        openDeploymentWindow(project, server);
    }

    /**
     * Creează DeploymentWindow, îi pasează referința la această fereastră
     * (pentru navigare înapoi), o afișează, și ascunde SelectionWindow.
     * <p>
     * {@code this.close()} ascunde fereastra — DeploymentWindow o poate
     * re-afișa prin {@code selectionWindow.show()} la "Change Project/Server".
     */
    private void openDeploymentWindow(Project project, Server server) {
        try {
            LOGGER.info("Opening deployment window with SFTP connection");

            DeploymentWindow deploymentWindow = new DeploymentWindow(project, server);
            deploymentWindow.setSelectionWindow(this);
            deploymentWindow.show();

            this.close();

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error opening deployment window", ex);
            CustomAlert.showError("Connection Error",
                    "Failed to open deployment window:\n" + ex.getMessage());
        }
    }

    /**
     * Dialogurile de management primesc un callback {@code onClosed} care
     * refreshează combo box-ul corespunzător — astfel modificările (add/edit/delete)
     * sunt reflectate imediat în lista de selecție.
     */
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

    /** Settings nu necesită refresh la închidere — nu afectează listele. */
    private void openSettings() {
        dialogManager.openDialog(
                SettingsDialog.class,
                () -> new SettingsDialog(true),
                "Settings",
                null
        );
    }

    @Override
    protected double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }
}