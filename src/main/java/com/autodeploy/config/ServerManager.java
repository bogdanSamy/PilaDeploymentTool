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

package com.autodeploy.config;

import com.autodeploy.model.Server;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing servers (save/load from JSON)
 *
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class ServerManager {
    private static ServerManager instance;
    private static final String SERVERS_FILE = "servers.json";
    private final ObjectMapper objectMapper;
    private List<Server> servers;

    private ServerManager() {
        objectMapper = new ObjectMapper();

        // Enable pretty printing
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        servers = new ArrayList<>();
        load();
    }

    public static synchronized ServerManager getInstance() {
        if (instance == null) {
            instance = new ServerManager();
        }
        return instance;
    }

    /**
     * Load servers from JSON file
     */
    private void load() {
        File file = new File(SERVERS_FILE);
        if (file.exists() && file.length() > 0) {
            try {
                servers = objectMapper.readValue(file, new TypeReference<List<Server>>() {});

                if (servers == null) {
                    servers = new ArrayList<>();
                }

                System.out.println("✓ Loaded " + servers.size() + " servers from " + SERVERS_FILE);

                // Debug: Print loaded servers
                for (Server server : servers) {
                    System.out.println("  - " + server.getName() + " (" + server.getHost() + ")");
                }

            } catch (IOException e) {
                System.err.println("✗ Could not load servers.json: " + e.getMessage());
                e.printStackTrace();
                servers = new ArrayList<>();
            }
        } else {
            System.out.println("ℹ No existing servers file found. Starting with empty server list.");
            servers = new ArrayList<>();
        }
    }

    /**
     * Save servers to JSON file
     */
    public void save() {
        try {
            File file = new File(SERVERS_FILE);
            objectMapper.writeValue(file, servers);
            System.out.println("✓ Saved " + servers.size() + " servers to " + SERVERS_FILE);
        } catch (IOException e) {
            System.err.println("✗ Could not save servers.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reload servers from file
     */
    public void reload() {
        System.out.println("↻ Reloading servers from file...");
        load();
    }

    /**
     * Get all servers
     */
    public List<Server> getServers() {
        return new ArrayList<>(servers);
    }

    /**
     * Add a new server
     */
    public void addServer(Server server) {
        if (server != null && server.isValid()) {
            servers.add(server);
            save();
            System.out.println("✓ Added server: " + server.getName());
        } else {
            System.err.println("✗ Cannot add invalid server");
        }
    }

    /**
     * Update an existing server
     */
    public void updateServer(Server oldServer, Server newServer) {
        int index = servers.indexOf(oldServer);
        if (index != -1 && newServer.isValid()) {
            newServer.setId(oldServer.getId()); // Keep the same ID
            servers.set(index, newServer);
            save();
            System.out.println("✓ Updated server: " + newServer.getName());
        } else {
            System.err.println("✗ Cannot update server - not found or invalid");
        }
    }

    /**
     * Delete a server
     */
    public void deleteServer(Server server) {
        if (servers.remove(server)) {
            save();
            System.out.println("✓ Deleted server: " + server.getName());
        } else {
            System.err.println("✗ Cannot delete server - not found");
        }
    }

    /**
     * Find server by name
     */
    public Server findServerByName(String name) {
        return servers.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Find server by host
     */
    public Server findServerByHost(String host) {
        return servers.stream()
                .filter(s -> s.getHost().equals(host))
                .findFirst()
                .orElse(null);
    }

    /**
     * Find server by ID
     */
    public Server findServerById(String id) {
        return servers.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get server count
     */
    public int getServerCount() {
        return servers.size();
    }

    /**
     * Clear all servers (for testing)
     */
    public void clear() {
        servers.clear();
        save();
        System.out.println("✓ Cleared all servers");
    }
}