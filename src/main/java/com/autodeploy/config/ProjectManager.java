package com.autodeploy.config;

import com.autodeploy.model.Project;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProjectManager {
    private static ProjectManager instance;
    private static final String PROJECTS_FILE = "projects.json";
    private final ObjectMapper objectMapper;
    private List<Project> projects;

    private ProjectManager() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        projects = new ArrayList<>();
        load();
    }

    public static synchronized ProjectManager getInstance() {
        if (instance == null) {
            instance = new ProjectManager();
        }
        return instance;
    }

    private void load() {
        File file = new File(PROJECTS_FILE);
        if (file.exists() && file.length() > 0) {
            try {
                projects = objectMapper.readValue(file, new TypeReference<List<Project>>() {});

                if (projects == null) {
                    projects = new ArrayList<>();
                }

                System.out.println("✓ Loaded " + projects.size() + " projects from " + PROJECTS_FILE);

                // Debug: Print loaded projects
                for (Project project : projects) {
                    System.out.println("  - " + project.getName() + " (ID: " + project.getId() + ")");
                }

            } catch (IOException e) {
                System.err.println("✗ Could not load projects.json: " + e.getMessage());
                e.printStackTrace();
                projects = new ArrayList<>();
            }
        } else {
            System.out.println("ℹ No existing projects file found. Starting with empty project list.");
            projects = new ArrayList<>();
        }
    }

    public void save() {
        try {
            File file = new File(PROJECTS_FILE);
            objectMapper.writeValue(file, projects);
            System.out.println("✓ Saved " + projects.size() + " projects to " + PROJECTS_FILE);
        } catch (IOException e) {
            System.err.println("✗ Could not save projects.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void reload() {
        System.out.println("Reloading projects from file...");
        load();
    }

    public List<Project> getProjects() {
        return new ArrayList<>(projects);
    }

    public void addProject(Project project) {
        if (project != null && project.isValid()) {
            projects.add(project);
            save();
            System.out.println("✓ Added project: " + project.getName());
        } else {
            System.err.println("✗ Cannot add invalid project");
        }
    }

    public void updateProject(Project oldProject, Project newProject) {
        int index = projects.indexOf(oldProject);
        if (index != -1 && newProject.isValid()) {
            newProject.setId(oldProject.getId()); // Keep the same ID
            projects.set(index, newProject);
            save();
            System.out.println("✓ Updated project: " + newProject.getName());
        } else {
            System.err.println("✗ Cannot update project - not found or invalid");
        }
    }

    public void deleteProject(Project project) {
        if (projects.remove(project)) {
            save();
            System.out.println("✓ Deleted project: " + project.getName());
        } else {
            System.err.println("✗ Cannot delete project - not found");
        }
    }

    public Project findProjectByName(String name) {
        return projects.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}