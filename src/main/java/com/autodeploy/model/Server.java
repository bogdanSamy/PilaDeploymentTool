package com.autodeploy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

public class Server {

    private static final String DEFAULT_RESTART_MANAGER_SCRIPT = "/nodel/testeUpload/restart_manager.sh";
    private static final int DEFAULT_PORT = 22;

    private String id, name, host, username,password,restartManagerScript;
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

    // =================================================================================================================

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

    public String getRestartManagerScript() {
        return (restartManagerScript != null && !restartManagerScript.isEmpty())
                ? restartManagerScript
                : DEFAULT_RESTART_MANAGER_SCRIPT;
    }

    public void setRestartManagerScript(String restartManagerScript) {
        this.restartManagerScript = restartManagerScript;
    }

    // =================================================================================================================

    @JsonIgnore
    public boolean isValid() {
        return isNotEmpty(name) && isNotEmpty(host) && isNotEmpty(username);
    }

    private boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    // =================================================================================================================

    @Override
    public String toString() {
        return name + " (" + host + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return Objects.equals(id, server.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}