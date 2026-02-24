package com.autodeploy.ui.dialog;

import com.autodeploy.core.assets.Assets;
import com.autodeploy.domain.manager.ServerManager;
import com.autodeploy.domain.model.Server;
import com.autodeploy.ui.dialog.component.ServerFormBinder;
import com.autodeploy.ui.dialog.helper.WindowDecorationHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.autodeploy.core.constants.Constants.TITLE_BAR_HEIGHT;
import static com.autodeploy.core.constants.Constants.VALIDATION_MESSAGE;

/**
 * Dialog CRUD pentru managementul serverelor SSH/SFTP.
 * <p>
 * Structură similară cu {@link ProjectManagementDialog}: tabel + form + acțiuni CRUD.
 * Validarea formularului e delegată la {@link ServerFormBinder}.
 */
public class ServerManagementDialog extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML private Button closeBtn;
    @FXML private TableView<Server> serversTable;
    @FXML private TableColumn<Server, String> nameColumn;
    @FXML private TableColumn<Server, String> hostColumn;
    @FXML private TextField nameField;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField restartScriptField;
    @FXML private Button addBtn;
    @FXML private Button updateBtn;
    @FXML private Button deleteBtn;
    @FXML private Button clearBtn;
    @FXML private Button closeDialogBtn;

    private final ServerManager serverManager;
    private final ObservableList<Server> serversList;

    private ServerFormBinder formBinder;
    private Server selectedServer;

    public ServerManagementDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.serverManager = ServerManager.getInstance();
        this.serversList = FXCollections.observableArrayList();
        try {
            Parent parent = Assets.loadFxml("/fxml/server-management.fxml", this);
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
        loadServers();
        formBinder.applyDefaults();
        formBinder.setupPortValidation();
        bindActions();
        bindListeners();

        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);
    }

    private void initComponents() {
        formBinder = new ServerFormBinder(
                nameField, hostField, portField,
                usernameField, passwordField, restartScriptField
        );
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));
        hostColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getHost()));
        serversTable.setItems(serversList);
    }

    private void bindActions() {
        closeBtn.setOnAction(e -> close());
        closeDialogBtn.setOnAction(e -> close());

        addBtn.setOnAction(e -> addServer());
        updateBtn.setOnAction(e -> updateServer());
        deleteBtn.setOnAction(e -> deleteServer());
        clearBtn.setOnAction(e -> clearForm());
    }

    private void bindListeners() {
        serversTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                formBinder.loadServer(newSel);
                selectedServer = newSel;
                updateBtn.setDisable(false);
                deleteBtn.setDisable(false);
            } else {
                selectedServer = null;
                updateBtn.setDisable(true);
                deleteBtn.setDisable(true);
            }
        });
    }

    private void addServer() {
        if (!formBinder.isValid()) {
            CustomAlert.showError("Validation Error", VALIDATION_MESSAGE);
            return;
        }

        serverManager.addServer(formBinder.buildServerFromFields());
        loadServers();
        clearForm();
        CustomAlert.showInfo("Success", "Server added successfully!");
    }

    private void updateServer() {
        if (selectedServer == null) {
            CustomAlert.showError("Selection Error", "Please select a server to update.");
            return;
        }
        if (!formBinder.isValid()) {
            CustomAlert.showError("Validation Error", VALIDATION_MESSAGE);
            return;
        }

        serverManager.updateServer(selectedServer, formBinder.buildServerFromFields());
        loadServers();
        clearForm();
        CustomAlert.showInfo("Success", "Server updated successfully!");
    }

    private void deleteServer() {
        if (selectedServer == null) {
            CustomAlert.showError("Selection Error", "Please select a server to delete.");
            return;
        }

        boolean confirmed = CustomAlert.showConfirmation(
                this, "Delete Server",
                "Are you sure you want to delete the server '" + selectedServer.getName() + "'?"
        );

        if (confirmed) {
            serverManager.deleteServer(selectedServer);
            loadServers();
            clearForm();
            CustomAlert.showInfo("Success", "Server deleted successfully!");
        }
    }

    private void clearForm() {
        formBinder.clearAll();
        serversTable.getSelectionModel().clearSelection();
        selectedServer = null;
    }

    private void loadServers() {
        serversList.clear();
        serverManager.reload();
        List<Server> servers = serverManager.getServers();
        serversList.addAll(servers);
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