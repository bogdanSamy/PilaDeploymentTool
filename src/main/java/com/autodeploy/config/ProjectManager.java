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

import com.autodeploy.model.Project;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing projects (save/load from JSON)
 *
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class ProjectManager {
    private static ProjectManager instance;
    private static final String PROJECTS_FILE = "projects.json";
    private final ObjectMapper objectMapper;
    private List<Project> projects;

    private ProjectManager() {
        objectMapper = new ObjectMapper();

        // Enable pretty printing
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Ignore unknown properties during deserialization
        // This prevents errors when loading old JSON files with extra fields
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

    /**
     * Load projects from JSON file
     */
    private void load() {
        File file = new File(PROJECTS_FILE);
        if (file.exists() && file.length() > 0) {
            try {
                // Use TypeReference for proper deserialization of List<Project>
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

    /**
     * Save projects to JSON file
     */
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

    /**
     * Reload projects from file
     */
    public void reload() {
        System.out.println("↻ Reloading projects from file...");
        load();
    }

    /**
     * Get all projects
     */
    public List<Project> getProjects() {
        return new ArrayList<>(projects);
    }

    /**
     * Add a new project
     */
    public void addProject(Project project) {
        if (project != null && project.isValid()) {
            projects.add(project);
            save();
            System.out.println("✓ Added project: " + project.getName());
        } else {
            System.err.println("✗ Cannot add invalid project");
        }
    }

    /**
     * Update an existing project
     */
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

    /**
     * Delete a project
     */
    public void deleteProject(Project project) {
        if (projects.remove(project)) {
            save();
            System.out.println("✓ Deleted project: " + project.getName());
        } else {
            System.err.println("✗ Cannot delete project - not found");
        }
    }

    /**
     * Find project by name
     */
    public Project findProjectByName(String name) {
        return projects.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Find project by ID
     */
    public Project findProjectById(String id) {
        return projects.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get project count
     */
    public int getProjectCount() {
        return projects.size();
    }

    /**
     * Clear all projects (for testing)
     */
    public void clear() {
        projects.clear();
        save();
        System.out.println("✓ Cleared all projects");
    }
}