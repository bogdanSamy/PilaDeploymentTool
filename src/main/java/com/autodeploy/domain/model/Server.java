package com.autodeploy.domain.model;

import com.autodeploy.core.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

/**
 * Configurație de server SSH/SFTP pentru deployment.
 * Persistat în servers.json prin {@link com.autodeploy.domain.manager.ServerManager}.
 * <p>
 * <b>Atenție:</b> Parola este stocată în clar (plaintext) în fișierul JSON.
 * Acceptabil pentru un tool intern; pentru producție ar trebui integrare
 * cu un credential store (Keychain, Windows Credential Manager, etc.).
 */
public class Server {

    /** Script implicit pentru restart manager — suprascris per-server dacă e necesar. */
    private static final String DEFAULT_RESTART_MANAGER_SCRIPT = "/nodel/testeUpload/restart_manager.sh";
    private static final int DEFAULT_PORT = 22;

    private String id;
    private String name;
    private String host;
    private String username;
    private String password;
    private String restartManagerScript;
    private int port;

    public Server() {
        this.id = UUID.randomUUID().toString();
        this.port = DEFAULT_PORT;
    }

    public Server(String name, String host, int port, String username, String password) {
        this();
        this.name = name;
        this.host = host;
        this.port = (port > 0) ? port : DEFAULT_PORT;
        this.username = username;
        this.password = password;
    }

    @JsonCreator
    public Server(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("restartManagerScript") String restartManagerScript
    ) {
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.name = name;
        this.host = host;
        this.port = (port > 0) ? port : DEFAULT_PORT;
        this.username = username;
        this.password = password;
        this.restartManagerScript = restartManagerScript;
    }

    // --- Getters / Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    /** Returnează scriptul configurat sau default-ul dacă nu e setat. */
    public String getRestartManagerScript() {
        return StringUtils.defaultIfEmpty(restartManagerScript, DEFAULT_RESTART_MANAGER_SCRIPT);
    }

    public void setRestartManagerScript(String restartManagerScript) {
        this.restartManagerScript = restartManagerScript;
    }

    @JsonIgnore
    public boolean isValid() {
        return StringUtils.isNotEmpty(name)
                && StringUtils.isNotEmpty(host)
                && port > 0
                && StringUtils.isNotEmpty(username)
                && StringUtils.isNotEmpty(password);
    }

    @Override
    public String toString() { return name + " (" + host + ")"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return Objects.equals(id, server.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}