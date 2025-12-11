package com.autodeploy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

public class Project {

    private String id, name, localJarPath, localJspPath, remoteJarPath, remoteJspPath, buildFilePath;

    // =================================================================================================================

    public Project() {
        this.id = UUID.randomUUID().toString();
    }

    public Project(String name) {
        this();
        this.name = name;
    }

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
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.name = name;
        this.localJarPath = localJarPath;
        this.localJspPath = localJspPath;
        this.remoteJarPath = remoteJarPath;
        this.remoteJspPath = remoteJspPath;
        this.buildFilePath = buildFilePath;
    }

    // =================================================================================================================

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

    // =================================================================================================================

    @JsonIgnore
    public boolean isValid() {
        return isNotEmpty(name)
                && isNotEmpty(localJarPath)
                && isNotEmpty(localJspPath)
                && isNotEmpty(remoteJarPath)
                && isNotEmpty(remoteJspPath)
                && isNotEmpty(buildFilePath);
    }

    private boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    // =================================================================================================================

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}