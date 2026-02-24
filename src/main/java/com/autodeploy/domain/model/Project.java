package com.autodeploy.domain.model;

import com.autodeploy.core.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Configurație de proiect pentru deployment.
 * <p>
 * Definește căile locale (surse build) și remote (destinație SFTP):
 * <ul>
 *   <li>JAR — fișierele compilate (din build Ant)</li>
 *   <li>JSP — paginile web (copiate direct)</li>
 * </ul>
 * Persistat în projects.json prin {@link com.autodeploy.domain.manager.ProjectManager}.
 */
public class Project {

    private String id;
    private String name;
    private String localJarPath;
    private String localJspPath;
    private String remoteJarPath;
    private String remoteJspPath;
    private String buildFilePath;
    private String antTarget;
    private String antCommand;
    private List<String> antLibraries;

    public Project() {
        this.id = UUID.randomUUID().toString();
        this.antLibraries = new ArrayList<>();
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
            @JsonProperty("buildFilePath") String buildFilePath,
            @JsonProperty("antTarget") String antTarget,
            @JsonProperty("antCommand") String antCommand,
            @JsonProperty("antLibraries") List<String> antLibraries
    ) {
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.name = name;
        this.localJarPath = localJarPath;
        this.localJspPath = localJspPath;
        this.remoteJarPath = remoteJarPath;
        this.remoteJspPath = remoteJspPath;
        this.buildFilePath = buildFilePath;
        this.antTarget = antTarget;
        this.antCommand = antCommand;
        this.antLibraries = (antLibraries != null) ? new ArrayList<>(antLibraries) : new ArrayList<>();
    }

    // --- Getters / Setters (neschimbate) ---

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

    public String getAntTarget() { return antTarget; }
    public void setAntTarget(String antTarget) { this.antTarget = antTarget; }

    public String getAntCommand() { return antCommand; }
    public void setAntCommand(String antCommand) { this.antCommand = antCommand; }

    public List<String> getAntLibraries() { return antLibraries; }
    public void setAntLibraries(List<String> antLibraries) {
        this.antLibraries = (antLibraries != null) ? new ArrayList<>(antLibraries) : new ArrayList<>();
    }

    /**
     * Validare minimală: toate căile și configurările de build sunt obligatorii.
     * Ant libraries e opțional (unele proiecte nu au dependențe externe).
     */
    @JsonIgnore
    public boolean isValid() {
        return StringUtils.isNotEmpty(name)
                && StringUtils.isNotEmpty(localJarPath)
                && StringUtils.isNotEmpty(localJspPath)
                && StringUtils.isNotEmpty(remoteJarPath)
                && StringUtils.isNotEmpty(remoteJspPath)
                && StringUtils.isNotEmpty(buildFilePath)
                && StringUtils.isNotEmpty(antTarget)
                && StringUtils.isNotEmpty(antCommand);
    }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}