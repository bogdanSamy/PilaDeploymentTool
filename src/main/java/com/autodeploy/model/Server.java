package com.autodeploy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class Server {
    private String id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;

    public Server() {
        this.id = UUID.randomUUID().toString();
        this.port = 22;
    }

    public Server(String name, String host, int port, String username, String password) {
        this();
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    // Constructor for Jackson deserialization
    @JsonCreator
    public Server(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("username") String username,
            @JsonProperty("password") String password
    ) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.host = host;
        this.port = port > 0 ? port : 22;
        this.username = username;
        this.password = password;
    }

    // Getters and setters
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

    @JsonIgnore
    public boolean isValid() {
        return name != null && !name.isEmpty()
                && host != null && !host.isEmpty()
                && username != null && !username.isEmpty();
    }

    @Override
    public String toString() {
        return name + " (" + host + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return id != null ? id.equals(server.id) : server.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}