package com.autodeploy.ui.dialog.component;

import com.autodeploy.domain.model.Server;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Binding bidirecțional între formularul de server și modelul {@link Server}.
 * <p>
 * Include valori default pentru câmpurile comune (port 22, user/pass dev/dev)
 * și validare numerică pe câmpul de port (rejectă caractere non-digit).
 */
public class ServerFormBinder {

    private static final String DEFAULT_USERNAME = "dev";
    private static final String DEFAULT_PASSWORD = "dev";
    private static final String DEFAULT_PORT = "22";
    private static final int DEFAULT_PORT_INT = 22;
    private static final String DEFAULT_RESTART_SCRIPT = "/nodel/RestartManager/restart_manager.sh";

    private final TextField nameField;
    private final TextField hostField;
    private final TextField portField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final TextField restartScriptField;

    public ServerFormBinder(TextField nameField, TextField hostField, TextField portField,
                            TextField usernameField, PasswordField passwordField,
                            TextField restartScriptField) {
        this.nameField = nameField;
        this.hostField = hostField;
        this.portField = portField;
        this.usernameField = usernameField;
        this.passwordField = passwordField;
        this.restartScriptField = restartScriptField;
    }

    public void applyDefaults() {
        usernameField.setText(DEFAULT_USERNAME);
        passwordField.setText(DEFAULT_PASSWORD);
        portField.setText(DEFAULT_PORT);
    }

    /**
     * Restricționează input-ul la cifre. Dacă user-ul introduce un caracter
     * non-numeric, câmpul revine la valoarea anterioară.
     */
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
    }

    public Server buildServerFromFields() {
        Server server = new Server();
        server.setName(nameField.getText().trim());
        server.setHost(hostField.getText().trim());
        server.setPort(parsePort());
        server.setUsername(usernameField.getText().trim());
        server.setPassword(passwordField.getText()); // parolele pot conține spații — nu trim()
        server.setRestartManagerScript(restartScriptField.getText().trim());
        return server;
    }

    /** Clear + restore defaults (port, username, password rămân pre-populte). */
    public void clearAll() {
        nameField.clear();
        hostField.clear();
        portField.setText(DEFAULT_PORT);
        usernameField.setText(DEFAULT_USERNAME);
        passwordField.setText(DEFAULT_PASSWORD);
        restartScriptField.setText(DEFAULT_RESTART_SCRIPT);
    }

    public boolean isValid() {
        return !nameField.getText().trim().isEmpty()
                && !hostField.getText().trim().isEmpty()
                && !portField.getText().trim().isEmpty()
                && !usernameField.getText().trim().isEmpty()
                && !passwordField.getText().isEmpty()
                && !restartScriptField.getText().trim().isEmpty();
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