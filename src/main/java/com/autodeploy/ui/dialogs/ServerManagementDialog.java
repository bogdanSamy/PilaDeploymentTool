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
import com.autodeploy.config.ServerManager;
import com.autodeploy.model.Server;
import javafx.beans.property.SimpleIntegerProperty;
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

/**
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class ServerManagementDialog extends AbstractNfxUndecoratedWindow implements Initializable {

    @FXML
    private Button closeBtn;

    @FXML
    private TableView<Server> serversTable;

    @FXML
    private TableColumn<Server, String> nameColumn;

    @FXML
    private TableColumn<Server, String> hostColumn;

    @FXML
    private TableColumn<Server, Number> portColumn;

    @FXML
    private TextField nameField;

    @FXML
    private TextField hostField;

    @FXML
    private TextField portField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

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
     * Server manager instance
     */
    private final ServerManager serverManager;

    /**
     * Observable list of servers
     */
    private final ObservableList<Server> serversList;

    /**
     * Currently selected server for editing
     */
    private Server selectedServer;

    /**
     * Constructs a new instance of ServerManagementDialog with an option to hide from the taskbar.
     *
     * @param hideFromTaskBar Indicates whether the dialog should be hidden from the taskbar.
     */
    public ServerManagementDialog(boolean hideFromTaskBar) {
        super(hideFromTaskBar);
        this.serverManager = ServerManager.getInstance();
        this.serversList = FXCollections.observableArrayList();
        try {
            Parent parent = Assets.load("/server-management.fxml", this);
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

        // Close button
        closeBtn.setOnAction(event -> close());

        // Close dialog button
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

    /**
     * Setup table columns
     */
    private void setupTable() {
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getName()));

        hostColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getHost()));

        portColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(cellData.getValue().getPort()));

        serversTable.setItems(serversList);
    }

    /**
     * Load servers from ServerManager
     */
    private void loadServers() {
        serversList.clear();

        // Reload from file to ensure we have the latest data
        serverManager.reload();

        List<Server> servers = serverManager.getServers();
        serversList.addAll(servers);

        System.out.println("Loaded " + servers.size() + " servers into table");
    }

    /**
     * Load server data into form fields
     */
    private void loadServerToFields(Server server) {
        nameField.setText(server.getName());
        hostField.setText(server.getHost());
        portField.setText(String.valueOf(server.getPort()));
        usernameField.setText(server.getUsername() != null ? server.getUsername() : "");
        passwordField.setText(server.getPassword() != null ? server.getPassword() : "");
    }

    /**
     * Clear all form fields
     */
    private void clearFields() {
        nameField.clear();
        hostField.clear();
        portField.setText("22");
        usernameField.clear();
        passwordField.clear();
        serversTable.getSelectionModel().clearSelection();
        selectedServer = null;
    }

    /**
     * Add new server
     */
    private void addServer() {
        if (!validateFields()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields:\n- Name\n- Host\n- Username");
            return;
        }

        Server server = new Server();
        server.setName(nameField.getText().trim());
        server.setHost(hostField.getText().trim());

        try {
            int port = Integer.parseInt(portField.getText().trim());
            server.setPort(port);
        } catch (NumberFormatException e) {
            server.setPort(22);
        }

        server.setUsername(usernameField.getText().trim());
        server.setPassword(passwordField.getText());

        serverManager.addServer(server);
        loadServers();
        clearFields();

        CustomAlert.showInfo("Success", "Server added successfully!");
    }

    /**
     * Update existing server
     */
    private void updateServer() {
        if (selectedServer == null) {
            CustomAlert.showError("Selection Error", "Please select a server to update.");
            return;
        }

        if (!validateFields()) {
            CustomAlert.showError("Validation Error", "Please fill in all required fields:\n- Name\n- Host\n- Username");
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

        serverManager.updateServer(selectedServer, updatedServer);
        loadServers();
        clearFields();

        CustomAlert.showInfo("Success", "Server updated successfully!");
    }

    /**
     * Delete selected server
     */
    private void deleteServer() {
        if (selectedServer == null) {
            CustomAlert.showError("Selection Error", "Please select a server to delete.");
            return;
        }

        boolean confirmed = CustomAlert.showConfirmation(
                "Delete Server",
                "Are you sure you want to delete the server '" + selectedServer.getName() + "'?"
        );

        if (confirmed) {
            serverManager.deleteServer(selectedServer);
            loadServers();
            clearFields();
            CustomAlert.showInfo("Success", "Server deleted successfully!");
        }
    }

    /**
     * Validate required fields
     */
    private boolean validateFields() {
        return !nameField.getText().trim().isEmpty()
                && !hostField.getText().trim().isEmpty()
                && !usernameField.getText().trim().isEmpty();
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