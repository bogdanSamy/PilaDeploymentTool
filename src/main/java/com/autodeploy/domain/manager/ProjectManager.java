package com.autodeploy.domain.manager;

import com.autodeploy.domain.model.Project;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public class ProjectManager extends JsonFileManager<Project> {

    private static final String PROJECTS_FILE = "projects.json";
    private static ProjectManager instance;

    private ProjectManager() {
        super(PROJECTS_FILE, new TypeReference<List<Project>>() {}, "project");
    }

    public static synchronized ProjectManager getInstance() {
        if (instance == null) {
            instance = new ProjectManager();
        }
        return instance;
    }

    @Override
    protected boolean isValid(Project project) {
        return project.isValid();
    }

    @Override
    protected String getDisplayName(Project project) {
        return project.getName();
    }

    @Override
    protected void preserveId(Project oldProject, Project newProject) {
        newProject.setId(oldProject.getId());
    }

    public List<Project> getProjects() { return getAll(); }
    public void addProject(Project project) { add(project); }
    public void updateProject(Project oldProject, Project newProject) { update(oldProject, newProject); }
    public void deleteProject(Project project) { delete(project); }
}