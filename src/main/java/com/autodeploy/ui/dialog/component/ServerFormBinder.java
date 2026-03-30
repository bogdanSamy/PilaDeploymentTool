package com.autodeploy.ui.dialog.component;

import com.autodeploy.domain.model.Server;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ServerFormBinder {

    private static final String DEFAULT_USERNAME = "dev";
    private static final String DEFAULT_PASSWORD = "dev";
    private static final String DEFAULT_PORT = "22";
    private static final int DEFAULT_PORT_INT = 22;
    public static final String DEFAULT_RESTART_SCRIPT = "/nodel/RestartManager/restart_manager.sh";
    private static final String DEFAULT_REMOTE_LOG_PATH = Server.DEFAULT_REMOTE_LOG_PATH;


    private final TextField nameField;
    private final TextField hostField;
    private final TextField portField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final TextField restartScriptField;
    private final TextField remoteLogPathField;

    public ServerFormBinder(TextField nameField, TextField hostField, TextField portField,
                            TextField usernameField, PasswordField passwordField,
                            TextField restartScriptField, TextField remoteLogPathField) {
        this.nameField = nameField;
        this.hostField = hostField;
        this.portField = portField;
        this.usernameField = usernameField;
        this.passwordField = passwordField;
        this.restartScriptField = restartScriptField;
        this.remoteLogPathField = remoteLogPathField;
    }

    public void applyDefaults() {
        usernameField.setText(DEFAULT_USERNAME);
        passwordField.setText(DEFAULT_PASSWORD);
        portField.setText(DEFAULT_PORT);
        remoteLogPathField.setText(DEFAULT_REMOTE_LOG_PATH);
    }

    public void setupPortValidation() {
        portField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                portField.setText(oldValue);
            }
        });
    }

    public void loadServer(Server server) {
        nameField.setText(server.getName());
        hostField.setText(server.getHost());
        portField.setText(String.valueOf(server.getPort()));
        usernameField.setText(nullSafe(server.getUsername()));
        passwordField.setText(nullSafe(server.getPassword()));
        restartScriptField.setText(
                server.getRestartManagerScript() != null
                        ? server.getRestartManagerScript()
                        : DEFAULT_RESTART_SCRIPT
        );
        remoteLogPathField.setText(nullSafe(server.getRemoteLogPath()));
    }

    public Server buildServerFromFields() {
        Server server = new Server();
        server.setName(nameField.getText().trim());
        server.setHost(hostField.getText().trim());
        server.setPort(parsePort());
        server.setUsername(usernameField.getText().trim());
        server.setPassword(passwordField.getText());
        server.setRestartManagerScript(restartScriptField.getText().trim());
        server.setRemoteLogPath(remoteLogPathField.getText().trim());
        return server;
    }

    public void clearAll() {
        nameField.clear();
        hostField.clear();
        portField.setText(DEFAULT_PORT);
        usernameField.setText(DEFAULT_USERNAME);
        passwordField.setText(DEFAULT_PASSWORD);
        restartScriptField.setText(DEFAULT_RESTART_SCRIPT);
        remoteLogPathField.setText(DEFAULT_REMOTE_LOG_PATH);
    }

    public boolean isValid() {
        return !nameField.getText().trim().isEmpty()
                && !hostField.getText().trim().isEmpty()
                && !portField.getText().trim().isEmpty()
                && !usernameField.getText().trim().isEmpty()
                && !passwordField.getText().isEmpty()
                && !restartScriptField.getText().trim().isEmpty();
        // remoteLogPath e opțional — nu e în validare
    }

    private int parsePort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT_INT;
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}