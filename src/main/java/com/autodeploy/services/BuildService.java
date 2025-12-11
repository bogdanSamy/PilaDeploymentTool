package com.autodeploy.services;

import com.autodeploy.config.ApplicationConfig;
import com.autodeploy.model.Project;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for building projects using Ant
 */
public class BuildService {

    private static final Logger LOGGER = Logger.getLogger(BuildService.class.getName());

    private final Project project;
    private final Consumer<String> logger;
    private final ApplicationConfig appConfig;

    public BuildService(Project project, Consumer<String> logger) {
        this.project = project;
        this.logger = logger;
        this.appConfig = ApplicationConfig.getInstance();
    }

    /**
     * Build result
     */
    public static class BuildResult {
        private final boolean success;
        private final int exitCode;
        private final String errorMessage;

        public BuildResult(boolean success, int exitCode, String errorMessage) {
            this.success = success;
            this.exitCode = exitCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public int getExitCode() { return exitCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Validate build configuration
     */
    public BuildResult validateConfiguration() {
        // Check build file path
        if (project.getBuildFilePath() == null || project.getBuildFilePath().isEmpty()) {
            return new BuildResult(false, -1,
                    "Build file path is not configured for project: " + project.getName());
        }

        // Check if build file exists
        File buildFile = new File(project.getBuildFilePath());
        if (!buildFile.exists()) {
            return new BuildResult(false, -1,
                    "Build file does not exist: " + project.getBuildFilePath());
        }

        // Check Ant path
        String antPath = appConfig.getAntPath();
        if (antPath == null || antPath.isEmpty()) {
            return new BuildResult(false, -1,
                    "Ant executable path is not configured in app-config.properties");
        }

        // Check if Ant executable exists
        File antFile = new File(antPath);
        if (!antFile.exists()) {
            return new BuildResult(false, -1,
                    "Ant executable does not exist: " + antPath);
        }

        return new BuildResult(true, 0, null);
    }

    /**
     * Build project asynchronously
     */
    public Task<BuildResult> buildAsync() {
        return new Task<>() {
            @Override
            protected BuildResult call() {
                return buildProject();
            }
        };
    }

    /**
     * Build project synchronously
     */
    public BuildResult buildProject() {
        log("🔨 Starting project build...");
        log("Project: " + project.getName());

        // Validate configuration
        BuildResult validation = validateConfiguration();
        if (!validation.isSuccess()) {
            log("✗ " + validation.getErrorMessage());
            return validation;
        }

        File buildFile = new File(project.getBuildFilePath());
        String antPath = appConfig.getAntPath();

        log("✓ Build file: " + project.getBuildFilePath());
        log("✓ Ant path: " + antPath);

        try {
            // Build command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    antPath,
                    "-f",
                    project.getBuildFilePath()
            );

            // Set working directory
            processBuilder.directory(buildFile.getParentFile());
            processBuilder.redirectErrorStream(true);

            log("-------------------------------");
            log("▶ Executing: " + antPath + " -f " + buildFile.getName());
            log("▶ Working directory: " + buildFile.getParent());
            log("-------------------------------");

            // Start process
            Process process = processBuilder.start();

            // Read output in real-time
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    final String outputLine = line;
                    log(outputLine);
                }
            }

            // Wait for completion
            int exitCode = process.waitFor();

            log("-------------------------------");
            if (exitCode == 0) {
                log("✓ Build completed successfully (exit code: " + exitCode + ")");
                return new BuildResult(true, exitCode, null);
            } else {
                log("✗ Build failed with exit code: " + exitCode);
                return new BuildResult(false, exitCode, "Build failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Build error", e);
            log("✗ Build error: " + e.getMessage());
            return new BuildResult(false, -1, e.getMessage());
        } finally {
            log("-------------------------------");
        }
    }

    /**
     * Log message
     */
    private void log(String message) {
        Platform.runLater(() -> logger.accept(message));
    }
}