package com.autodeploy.ui.dialogs;

import com.autodeploy.assets.Assets;
import com.autodeploy.config.ServerManager;
import com.autodeploy.model.Server;
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

    private static final int TITLE_BAR_HEIGHT = 30;
    private final ServerManager serverManager;
    private final ObservableList<Server> serversList;
    private Server selectedServer;

    public ServerManagementDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.serverManager = ServerManager.getInstance();
        this.serversList = FXCollections.observableArrayList();
        try {
            Parent parent = Assets.load("/fxml/server-management.fxml", this);
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

        // Load servers
        loadServers();

        // Set default values for new entries
        usernameField.setText("dev");
        passwordField.setText("dev");
        portField.setText("22");

        closeBtn.setOnAction(event -> close());

        closeDialogBtn.setOnAction(event -> close());

        // Action buttons
        addBtn.setOnAction(event -> addServer());
        updateBtn.setOnAction(event -> updateServer());
        deleteBtn.setOnAction(event -> deleteServer());
        clearBtn.setOnAction(event -> clearFields());

        // Table selection listener
        serversTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                loadServerToFields(newSelection);
                selectedServer = newSelection;
                updateBtn.setDisable(false);
                deleteBtn.setDisable(false);
            } else {
                selectedServer = null;
                updateBtn.setDisable(true);
                deleteBtn.setDisable(true);
            }
        });

        // Initially disable update/delete buttons
        updateBtn.setDisable(true);
        deleteBtn.setDisable(true);

        // Port field validation (only numbers)
        portField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                portField.setText(oldValue);
            }
        });
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));

        hostColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getHost()));

        serversTable.setItems(serversList);
    }

    private void loadServers() {
        serversList.clear();

        serverManager.reload();

        List<Server> servers = serverManager.getServers();
        serversList.addAll(servers);

        System.out.println("Loaded " + servers.size() + " servers into table");
    }

    private void loadServerToFields(Server server) {
        nameField.setText(server.getName());
        hostField.setText(server.getHost());
        portField.setText(String.valueOf(server.getPort()));
        usernameField.setText(server.getUsername() != null ? server.getUsername() : "");
        passwordField.setText(server.getPassword() != null ? server.getPassword() : "");
        restartScriptField.setText(server.getRestartManagerScript() != null
                ? server.getRestartManagerScript()
                : "/nodel/RestartManager/restart_manager.sh");
    }

    private void clearFields() {
        nameField.clear();
        hostField.clear();
        portField.setText("22");
        usernameField.setText("dev");
        passwordField.setText("dev");
        restartScriptField.setText("/nodel/RestartManager/restart_manager.sh");
        serversTable.getSelectionModel().clearSelection();
        selectedServer = null;
    }

    private void addServer() {
        if (!validateFields()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields:\n- Name\n- Host\n- Port\n- Username\n- Password\n- Restart Script Path");
            return;
        }

        Server server = new Server();
        server.setName(nameField.getText().trim());
        server.setHost(hostField.getText().trim());

        try {
            int port = Integer.parseInt(portField.getText().trim());
            server.setPort(port);
        } catch (NumberFormatException e) {
            // Should not happen due to regex validation, but fallback just in case
            server.setPort(22);
        }

        server.setUsername(usernameField.getText().trim());
        server.setPassword(passwordField.getText());
        server.setRestartManagerScript(restartScriptField.getText().trim());

        serverManager.addServer(server);
        loadServers();
        clearFields();

        CustomAlert.showInfo("Success", "Server added successfully!");
    }

    private void updateServer() {
        if (selectedServer == null) {
            CustomAlert.showError("Selection Error", "Please select a server to update.");
            return;
        }

        if (!validateFields()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields:\n- Name\n- Host\n- Port\n- Username\n- Password\n- Restart Script Path");
            return;
        }

        Server updatedServer = new Server();
        updatedServer.setName(nameField.getText().trim());
        updatedServer.setHost(hostField.getText().trim());

        try {
            int port = Integer.parseInt(portField.getText().trim());
            updatedServer.setPort(port);
        } catch (NumberFormatException e) {
            updatedServer.setPort(22);
        }

        updatedServer.setUsername(usernameField.getText().trim());
        updatedServer.setPassword(passwordField.getText());
        updatedServer.setRestartManagerScript(restartScriptField.getText().trim());

        serverManager.updateServer(selectedServer, updatedServer);
        loadServers();
        clearFields();

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
            clearFields();
            CustomAlert.showInfo("Success", "Server deleted successfully!");
        }
    }

    private boolean validateFields() {
        return !nameField.getText().trim().isEmpty()
                && !hostField.getText().trim().isEmpty()
                && !portField.getText().trim().isEmpty()
                && !usernameField.getText().trim().isEmpty()
                && !passwordField.getText().isEmpty()
                && !restartScriptField.getText().trim().isEmpty();
    }

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

    @Override
    public double getTitleBarHeight() {
        return TITLE_BAR_HEIGHT;
    }
}