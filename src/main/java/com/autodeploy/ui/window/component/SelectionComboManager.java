package com.autodeploy.ui.window.component;

import com.autodeploy.domain.manager.ProjectManager;
import com.autodeploy.domain.manager.ServerManager;
import com.autodeploy.domain.model.Project;
import com.autodeploy.domain.model.Server;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.util.logging.Logger;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Gestionează combo box-urile de selecție server/proiect din SelectionWindow.
 * <p>
 * Lucrează direct cu obiectele domeniului ({@link Server}, {@link Project}),
 * nu cu String-uri — elimină nevoia de lookup-uri și conversii la fiecare selecție.
 * {@link StringConverter} și {@link ListCell} asigură afișarea corectă.
 * <p>
 * La refresh (după editare în dialog), păstrează selecția curentă dacă
 * elementul încă există în lista actualizată.
 */
public class SelectionComboManager {

    private static final Logger LOGGER = Logger.getLogger(SelectionComboManager.class.getName());

    private final ComboBox<Server> serverComboBox;
    private final ComboBox<Project> projectComboBox;
    private final ServerManager serverManager;
    private final ProjectManager projectManager;

    private Runnable onSelectionChanged;

    public SelectionComboManager(ComboBox<Server> serverComboBox,
                                 ComboBox<Project> projectComboBox,
                                 ServerManager serverManager,
                                 ProjectManager projectManager) {
        this.serverComboBox = serverComboBox;
        this.projectComboBox = projectComboBox;
        this.serverManager = serverManager;
        this.projectManager = projectManager;
    }

    public void setup() {
        setupServerCombo();
        setupProjectCombo();
        loadAll();

        serverComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, newVal) -> notifySelectionChanged());
        projectComboBox.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, newVal) -> notifySelectionChanged());
    }

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    public Server getSelectedServer() {
        return serverComboBox.getSelectionModel().getSelectedItem();
    }

    public Project getSelectedProject() {
        return projectComboBox.getSelectionModel().getSelectedItem();
    }

    public boolean isBothSelected() {
        return getSelectedServer() != null && getSelectedProject() != null;
    }

    /**
     * Reîncarcă lista, păstrând selecția curentă dacă elementul
     * încă există (comparare prin equals/id).
     */
    public void refreshServers() {
        refreshComboBox(serverComboBox, () -> {
            serverManager.reload();
            serverComboBox.getItems().setAll(serverManager.getServers());
        }, "server");
    }

    public void refreshProjects() {
        refreshComboBox(projectComboBox, () -> {
            projectManager.reload();
            projectComboBox.getItems().setAll(projectManager.getProjects());
        }, "project");
    }

    private void setupServerCombo() {
        serverComboBox.setPromptText(SERVER_PROMPT);
        serverComboBox.setMaxWidth(Double.MAX_VALUE);

        serverComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Server server) {
                return server != null ? server.getName() + " (" + server.getHost() + ")" : "";
            }

            @Override
            public Server fromString(String string) { return null; }
        });

        serverComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Server item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName() + " (" + item.getHost() + ")");
            }
        });
    }

    private void setupProjectCombo() {
        projectComboBox.setPromptText(PROJECT_PROMPT);
        projectComboBox.setMaxWidth(Double.MAX_VALUE);

        projectComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Project project) {
                return project != null ? project.getName() : "";
            }

            @Override
            public Project fromString(String string) { return null; }
        });

        projectComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
    }

    private void loadAll() {
        serverComboBox.getItems().setAll(serverManager.getServers());
        projectComboBox.getItems().setAll(projectManager.getProjects());
        LOGGER.info(String.format("Loaded %d servers, %d projects",
                serverComboBox.getItems().size(), projectComboBox.getItems().size()));
    }

    /**
     * Pattern generic de refresh: salvează selecția, reîncarcă, restaurează selecția.
     * Funcționează cu orice tip datorită equals() pe ID (definit în Server/Project).
     */
    private <T> void refreshComboBox(ComboBox<T> comboBox, Runnable loadAction, String itemType) {
        T currentSelection = comboBox.getSelectionModel().getSelectedItem();
        loadAction.run();

        if (currentSelection != null && comboBox.getItems().contains(currentSelection)) {
            comboBox.getSelectionModel().select(currentSelection);
        }
        LOGGER.info(String.format("Refreshed %s list", itemType));
    }

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }
}