package com.autodeploy.config;

import com.autodeploy.model.Project;
import com.autodeploy.model.Server;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ConfigManager {
    private static final String CONFIG_FILE = System.getProperty("user.home") +
            File.separator + ".autodeploy-config-v2.json";
    private final Gson gson;

    private List<Server> servers = new ArrayList<>();
    private List<Project> projects = new ArrayList<>();
    private String lastSelectedServerId;
    private String lastSelectedProjectId;

    public ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    // Server management
    public List<Server> getServers() {
        return new ArrayList<>(servers);
    }

    public void addServer(Server server) {
        servers.add(server);
        save();
    }

    public void updateServer(Server server) {
        servers.removeIf(s -> s.getId().equals(server.getId()));
        servers.add(server);
        save();
    }

    public void deleteServer(String serverId) {
        servers.removeIf(s -> s.getId().equals(serverId));
        save();
    }

    // Project management
    public List<Project> getProjects() {
        return new ArrayList<>(projects);
    }

    public void addProject(Project project) {
        projects.add(project);
        save();
    }

    public void updateProject(Project project) {
        projects.removeIf(p -> p.getId().equals(project.getId()));
        projects.add(project);
        save();
    }

    public void deleteProject(String projectId) {
        projects.removeIf(p -> p.getId().equals(projectId));
        save();
    }

    // Selection tracking
    public String getLastSelectedServerId() {
        return lastSelectedServerId;
    }

    public void setLastSelectedServerId(String serverId) {
        this.lastSelectedServerId = serverId;
        save();
    }

    public String getLastSelectedProjectId() {
        return lastSelectedProjectId;
    }

    public void setLastSelectedProjectId(String projectId) {
        this.lastSelectedProjectId = projectId;
        save();
    }

    private void save() {
        ConfigData data = new ConfigData();
        data.servers = new ArrayList<>();
        data.projects = new ArrayList<>();
        data.lastSelectedServerId = lastSelectedServerId;
        data.lastSelectedProjectId = lastSelectedProjectId;

        // Salvare servere
        for (Server server : servers) {
            ServerData serverData = new ServerData();
            serverData.id = server.getId();
            serverData.name = server.getName();
            serverData.host = server.getHost();
            serverData.port = server.getPort();
            serverData.username = server.getUsername();
            serverData.password = encodePassword(server.getPassword());
            data.servers.add(serverData);
        }

        // Salvare proiecte
        for (Project project : projects) {
            ProjectData projectData = new ProjectData();
            projectData.id = project.getId();
            projectData.name = project.getName();
            projectData.localJarPath = project.getLocalJarPath();
            projectData.localJspPath = project.getLocalJspPath();
            projectData.remoteJarPath = project.getRemoteJarPath();
            projectData.remoteJspPath = project.getRemoteJspPath();

            projectData.buildFilePath = project.getBuildFilePath();
            data.projects.add(projectData);
        }

        try (Writer writer = new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
            System.out.println("Config saved to: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    private void load() {
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            System.out.println("Config file not found, starting fresh");
            return;
        }

        try (Reader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
            ConfigData data = gson.fromJson(reader, ConfigData.class);

            if (data != null) {
                this.lastSelectedServerId = data.lastSelectedServerId;
                this.lastSelectedProjectId = data.lastSelectedProjectId;

                // Încărcare servere
                if (data.servers != null) {
                    for (ServerData serverData : data.servers) {
                        Server server = new Server();
                        server.setId(serverData.id);
                        server.setName(serverData.name);
                        server.setHost(serverData.host);
                        server.setPort(serverData.port);
                        server.setUsername(serverData.username);
                        server.setPassword(decodePassword(serverData.password));
                        servers.add(server);
                    }
                }

                // Încărcare proiecte
                if (data.projects != null) {
                    for (ProjectData projectData : data.projects) {
                        Project project = new Project();
                        project.setId(projectData.id);
                        project.setName(projectData.name);
                        project.setLocalJarPath(projectData.localJarPath);
                        project.setLocalJspPath(projectData.localJspPath);
                        project.setRemoteJarPath(projectData.remoteJarPath);
                        project.setRemoteJspPath(projectData.remoteJspPath);

                        project.setBuildFilePath(projectData.buildFilePath);
                        projects.add(project);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String encodePassword(String password) {
        if (password == null || password.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    private String decodePassword(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public void saveProject(Project currentProject) {
    }

    public void saveServer(Server currentServer) {

    }

    // Clase interne pentru JSON
    private static class ConfigData {
        List<ServerData> servers;
        List<ProjectData> projects;
        String lastSelectedServerId;
        String lastSelectedProjectId;
    }

    private static class ServerData {
        String id;
        String name;
        String host;
        int port;
        String username;
        String password;
    }

    private static class ProjectData {
        String id;
        String name;
        String localJarPath;
        String localJspPath;
        String remoteJarPath;
        String remoteJspPath;
        boolean monitorJars;
        boolean monitorJsps;
        String buildFilePath;
    }
}
