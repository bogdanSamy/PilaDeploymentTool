package com.autodeploy.service.deploy;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.domain.model.Project;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        // Check Ant target
        if (project.getAntTarget() == null || project.getAntTarget().isEmpty()) {
            return new BuildResult(false, -1,
                    "Ant target is not configured for project: " + project.getName());
        }

        // Check Ant command
        if (project.getAntCommand() == null || project.getAntCommand().trim().isEmpty()) {
            return new BuildResult(false, -1,
                    "Ant command is not configured for project: " + project.getName());
        }

        // Check Ant path from config
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
        File workingDir = buildFile.getParentFile();
        String antCommand = project.getAntCommand();
        String antPath = appConfig.getAntPath();

        log("✓ Build file: " + project.getBuildFilePath());
        log("✓ Ant target: " + project.getAntTarget());
        log("✓ Ant path: " + antPath);
        log("✓ Working directory: " + workingDir.getAbsolutePath());

        // Prepare command - replace "ant" with full path
        String finalCommand = prepareCommand(antCommand, antPath);

        log("-------------------------------");
        log("▶ Command to execute:");
        for (String line : finalCommand.split("\\r?\\n")) {
            log("  " + line);
        }
        log("-------------------------------");

        File tempScript = null;
        try {
            // Create temporary script file
            tempScript = createTempScript(finalCommand, workingDir);
            log("✓ Created temp script: " + tempScript.getName());

            // Build command to execute the script
            List<String> command = new ArrayList<>();
            if (isWindows()) {
                command.add("cmd.exe");
                command.add("/c");
                command.add(tempScript.getAbsolutePath());
            } else {
                command.add("/bin/bash");
                command.add(tempScript.getAbsolutePath());
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDir);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().putAll(System.getenv());

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
            // Clean up temp script
            if (tempScript != null && tempScript.exists()) {
                try {
                    Files.delete(tempScript.toPath());
                    log("✓ Cleaned up temp script");
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to delete temp script", e);
                }
            }
            log("-------------------------------");
        }
    }

    /**
     * Replaces "ant " with the full ant path from config
     * Example: "ant -f build.xml" becomes "C:\apache-ant\bin\ant.bat -f build.xml"
     */
    private String prepareCommand(String command, String antPath) {
        StringBuilder result = new StringBuilder();

        for (String line : command.split("\\r?\\n")) {
            String trimmedLine = line.trim();

            // Check if line starts with "ant " or is exactly "ant"
            if (trimmedLine.equals("ant") || trimmedLine.startsWith("ant ")) {
                // Replace "ant" with full path (quoted if contains spaces)
                String quotedAntPath = antPath.contains(" ") ? "\"" + antPath + "\"" : antPath;
                result.append(quotedAntPath).append(trimmedLine.substring(3)); // Remove "ant", keep rest
            } else {
                result.append(line);
            }
            result.append("\n");
        }

        return result.toString().trim();
    }

    /**
     * Creates a temporary script file with the ant command
     */
    private File createTempScript(String command, File workingDir) throws Exception {
        String extension = isWindows() ? ".bat" : ".sh";
        File tempScript = File.createTempFile("ant_build_", extension, workingDir);

        try (FileWriter writer = new FileWriter(tempScript)) {
            if (isWindows()) {
                writer.write("@echo off\r\n");
                String windowsCommand = command.replace("\n", "\r\n");
                writer.write(windowsCommand);
                writer.write("\r\n");
            } else {
                writer.write("#!/bin/bash\n");
                String unixCommand = convertToUnixScript(command);
                writer.write(unixCommand);
                writer.write("\n");
            }
        }

        if (!isWindows()) {
            tempScript.setExecutable(true);
        }

        return tempScript;
    }

    /**
     * Converts Windows-style commands to Unix-style
     */
    private String convertToUnixScript(String command) {
        StringBuilder result = new StringBuilder();
        for (String line : command.split("\\r?\\n")) {
            String trimmedLine = line.trim();
            if (trimmedLine.toLowerCase().startsWith("set ")) {
                String varPart = trimmedLine.substring(4);
                result.append("export ").append(varPart);
            } else {
                result.append(line);
            }
            result.append("\n");
        }
        return result.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void log(String message) {
        Platform.runLater(() -> logger.accept(message));
    }
}