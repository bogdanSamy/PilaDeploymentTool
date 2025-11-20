package com.autodeploy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class Project {
    private String id;
    private String name;
    private String localJarPath;    // Calea locală unde se generează JAR-urile
    private String localJspPath;    // Calea locală unde sunt JSP-urile
    private String remoteJarPath;   // Calea pe server pentru JAR-uri
    private String remoteJspPath;   // Calea pe server pentru JSP-uri
    private String buildFilePath;    // Calea către fișierul de build (pom.xml, build.gradle, etc.)

    // Default constructor for Jackson
    public Project() {
        this.id = UUID.randomUUID().toString();
    }

    public Project(String name) {
        this();
        this.name = name;
    }

    // Constructor for Jackson deserialization
    @JsonCreator
    public Project(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("localJarPath") String localJarPath,
            @JsonProperty("localJspPath") String localJspPath,
            @JsonProperty("remoteJarPath") String remoteJarPath,
            @JsonProperty("remoteJspPath") String remoteJspPath,
            @JsonProperty("buildFilePath") String buildFilePath
    ) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.localJarPath = localJarPath;
        this.localJspPath = localJspPath;
        this.remoteJarPath = remoteJarPath;
        this.remoteJspPath = remoteJspPath;
        this.buildFilePath = buildFilePath;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocalJarPath() { return localJarPath; }
    public void setLocalJarPath(String localJarPath) { this.localJarPath = localJarPath; }

    public String getLocalJspPath() { return localJspPath; }
    public void setLocalJspPath(String localJspPath) { this.localJspPath = localJspPath; }

    public String getRemoteJarPath() { return remoteJarPath; }
    public void setRemoteJarPath(String remoteJarPath) { this.remoteJarPath = remoteJarPath; }

    public String getRemoteJspPath() { return remoteJspPath; }
    public void setRemoteJspPath(String remoteJspPath) { this.remoteJspPath = remoteJspPath; }

    public String getBuildFilePath() { return buildFilePath; }
    public void setBuildFilePath(String buildFilePath) { this.buildFilePath = buildFilePath; }

    // Mark this method as ignored by Jackson to prevent serialization
    @JsonIgnore
    public boolean isValid() {
        return name != null && !name.isEmpty()
                && localJarPath != null && !localJarPath.isEmpty()
                && remoteJarPath != null && !remoteJarPath.isEmpty();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return id != null ? id.equals(project.id) : project.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}