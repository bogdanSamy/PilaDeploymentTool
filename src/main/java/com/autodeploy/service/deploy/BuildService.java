package com.autodeploy.service.deploy;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.domain.model.Project;
import com.autodeploy.service.utility.OsHelper;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Execută build-ul Ant al unui proiect.
 * <p>
 * Fluxul:
 * <ol>
 *   <li>Validare configurație (cale Ant, build file, target)</li>
 *   <li>Pregătire comandă — înlocuiește "ant" cu calea absolută din config</li>
 *   <li>Generare script temporar (.bat pe Windows, .sh pe Linux/Mac)</li>
 *   <li>Execuție cu timeout de {@value BUILD_TIMEOUT_MINUTES} minute</li>
 *   <li>Streaming output-ului live către logger (vizibil în UI)</li>
 *   <li>Cleanup script temporar</li>
 * </ol>
 * <p>
 * De ce script temporar și nu execuție directă?
 * Scriptul setează environment-ul complet (JAVA_HOME, CLASSPATH, JAVACMD)
 * înainte de a lansa Ant — identic cu un .bat manual. Asta garantează că
 * build-ul e izolat de environment-ul procesului părinte (Maven, IntelliJ, etc.).
 */
public class BuildService {

    private static final Logger LOGGER = Logger.getLogger(BuildService.class.getName());
    private static final long BUILD_TIMEOUT_MINUTES = 10;

    /** Variables that ant.bat / ant.sh check before JAVA_HOME */
    private static final List<String> POISONOUS_ENV_VARS = List.of(
            "JAVACMD", "_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"
    );

    private final Project project;
    private final Consumer<String> logger;
    private final ApplicationConfig appConfig;

    public BuildService(Project project, Consumer<String> logger) {
        this.project = project;
        this.logger = logger;
        this.appConfig = ApplicationConfig.getInstance();
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /**
     * Validează configurația de build în lanț (fail-fast).
     * Folosește Optional chaining — prima validare eșuată oprește lanțul
     * și returnează BuildResult.failure cu mesajul corespunzător.
     */
    public BuildResult validateConfiguration() {
        return validateNotEmpty(project.getBuildFilePath(),
                "Build file path is not configured for project: " + project.getName())
                .or(() -> validateFileExists(project.getBuildFilePath(),
                        "Build file does not exist: " + project.getBuildFilePath()))
                .or(() -> validateNotEmpty(project.getAntTarget(),
                        "Ant target is not configured for project: " + project.getName()))
                .or(() -> validateNotEmpty(project.getAntCommand(),
                        "Ant command is not configured for project: " + project.getName()))
                .or(() -> validateNotEmpty(appConfig.getAntPath(),
                        "Ant executable path is not configured in app-config.properties"))
                .or(() -> validateFileExists(appConfig.getAntPath(),
                        "Ant executable does not exist: " + appConfig.getAntPath()))
                .or(() -> validateNotEmpty(appConfig.getJava8Home(),
                        "Java 8 home path is not configured. Go to Settings and set the Java 8 Home path."))
                .or(() -> validateFileExists(appConfig.getJava8Home(),
                        "Java 8 home directory does not exist: " + appConfig.getJava8Home()))
                .orElse(BuildResult.success(0));
    }

    public Task<BuildResult> buildAsync() {
        return new Task<>() {
            @Override
            protected BuildResult call() {
                return buildProject();
            }
        };
    }

    public BuildResult buildProject() {
        log("🔨 Starting project build...");
        log("Project: " + project.getName());

        BuildResult validation = validateConfiguration();
        if (!validation.isSuccess()) {
            log("✗ " + validation.getErrorMessage());
            return validation;
        }

        File buildFile = new File(project.getBuildFilePath());
        File workingDir = buildFile.getParentFile();
        String antPath = appConfig.getAntPath();

        logBuildInfo(workingDir, antPath);

        String finalCommand = prepareCommand(project.getAntCommand(), antPath);
        logCommand(finalCommand);

        File tempScript = null;
        try {
            tempScript = createTempScript(finalCommand, workingDir);
            log("✓ Created temp script: " + tempScript.getName());

            int exitCode = executeScript(tempScript, workingDir);

            log("-------------------------------");
            if (exitCode == 0) {
                log("✓ Build completed successfully (exit code: " + exitCode + ")");
                return BuildResult.success(exitCode);
            } else {
                log("✗ Build failed with exit code: " + exitCode);
                return BuildResult.failure(exitCode, "Build failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Build error", e);
            log("✗ Build error: " + e.getMessage());
            return BuildResult.failure(e.getMessage());
        } finally {
            cleanupTempScript(tempScript);
            log("-------------------------------");
        }
    }

    // ──────────────────────────────────────────────
    // Process execution
    // ──────────────────────────────────────────────

    /**
     * Execută scriptul temporar ca proces extern.
     * <p>
     * Environment-ul e setat minim — doar moștenim variabilele sistem,
     * excluzând cele care pot interfera cu Java 8.
     * Configurarea reală (JAVA_HOME, CLASSPATH, etc.) e în script.
     */
    private int executeScript(File script, File workingDir) throws Exception {
        List<String> command = buildShellCommand(script);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);
        sanitizeEnvironment(processBuilder);

        log("-------------------------------");

        Process process = processBuilder.start();
        streamOutput(process);

        boolean finished = process.waitFor(BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Build timed out after " + BUILD_TIMEOUT_MINUTES + " minutes");
        }

        return process.exitValue();
    }

    /**
     * Construiește comanda shell pentru a rula scriptul.
     */
    private List<String> buildShellCommand(File script) {
        List<String> command = new ArrayList<>();
        if (OsHelper.isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
        } else {
            command.add("/bin/bash");
        }
        command.add(script.getAbsolutePath());
        return command;
    }

    /**
     * Curăță environment-ul procesului — elimină variabilele moștenite
     * de la Maven/IntelliJ/Eclipse care pot interfera cu Ant + Java 8.
     * <p>
     * Exemplu: JAVACMD=C:/jdk_21/bin/java.exe (setat de Maven) face ca
     * ant.bat să ignore JAVA_HOME și să folosească Java 21 în loc de Java 8.
     */
    private void sanitizeEnvironment(ProcessBuilder processBuilder) {
        POISONOUS_ENV_VARS.forEach(var ->
                processBuilder.environment().remove(var)
        );
        // CLASSPATH is managed entirely by the script
        processBuilder.environment().remove("CLASSPATH");
    }

    /**
     * Citește output-ul procesului linie cu linie și îl trimite live către logger.
     */
    private void streamOutput(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log(line);
            }
        }
    }

    // ──────────────────────────────────────────────
    // Script generation
    // ──────────────────────────────────────────────

    /**
     * Înlocuiește "ant" de la începutul fiecărei linii cu calea completă către Ant.
     * Suportă comenzi multi-linie — fiecare linie e procesată independent.
     * <p>
     * Exemplu: "ant -f build.xml compile" → "\"C:\apache-ant\bin\ant.bat\" -f build.xml compile"
     */
    private String prepareCommand(String command, String antPath) {
        String quotedAntPath = antPath.contains(" ") ? "\"" + antPath + "\"" : antPath;

        StringBuilder result = new StringBuilder();
        for (String line : command.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("ant") || trimmed.startsWith("ant ")) {
                result.append(quotedAntPath).append(trimmed.substring(3));
            } else {
                result.append(line);
            }
            result.append("\n");
        }
        return result.toString().trim();
    }

    /**
     * Creează un script temporar în directorul de build.
     * Scriptul conține environment setup complet + comanda Ant.
     */
    private File createTempScript(String command, File workingDir) throws Exception {
        String extension = OsHelper.isWindows() ? ".bat" : ".sh";
        File tempScript = File.createTempFile("ant_build_", extension, workingDir);

        try (FileWriter writer = new FileWriter(tempScript)) {
            if (OsHelper.isWindows()) {
                writer.write("@echo off\r\n");
                writeWindowsEnvironment(writer);
                writer.write(command.replace("\n", "\r\n"));
                writer.write("\r\n");
            } else {
                writer.write("#!/bin/bash\n");
                writeUnixEnvironment(writer);
                writer.write(convertToUnixScript(command));
                writer.write("\n");
            }
        }

        if (!OsHelper.isWindows()) {
            tempScript.setExecutable(true);
        }

        return tempScript;
    }

    /**
     * Scrie environment-ul Windows în script (.bat).
     * <p>
     * Ordinea e importantă:
     * 1. Clear JAVACMD — ant.bat îl verifică ÎNAINTE de JAVA_HOME
     * 2. Set JAVA_HOME — controlează ce JVM folosește Ant
     * 3. Set CLASSPATH — librăriile necesare pentru compilare (ex: weblogic.jar)
     */
    private void writeWindowsEnvironment(FileWriter writer) throws Exception {
        for (String var : POISONOUS_ENV_VARS) {
            writer.write("set " + var + "=\r\n");
        }

        String java8Home = appConfig.getJava8Home();
        if (java8Home != null && !java8Home.trim().isEmpty()) {
            writer.write("set JAVA_HOME=" + java8Home + "\r\n");
        }

        String classpath = buildClasspath(";");
        if (!classpath.isEmpty()) {
            writer.write("set CLASSPATH=" + classpath + ";%CLASSPATH%\r\n");
        }
    }

    /**
     * Scrie environment-ul Unix în script (.sh).
     */
    private void writeUnixEnvironment(FileWriter writer) throws Exception {
        for (String var : POISONOUS_ENV_VARS) {
            writer.write("unset " + var + "\n");
        }

        String java8Home = appConfig.getJava8Home();
        if (java8Home != null && !java8Home.trim().isEmpty()) {
            writer.write("export JAVA_HOME=" + java8Home + "\n");
            writer.write("export PATH=" + java8Home + "/bin:$PATH\n");
        }

        String classpath = buildClasspath(":");
        if (!classpath.isEmpty()) {
            writer.write("export CLASSPATH=" + classpath + ":$CLASSPATH\n");
        }
    }

    /**
     * Construiește CLASSPATH-ul din antLibraries configurate pe proiect.
     * Fiecare proiect își definește propriile librării — nu hardcodăm nimic.
     *
     * @param separator ";" pe Windows, ":" pe Unix
     */
    private String buildClasspath(String separator) {
        List<String> antLibraries = project.getAntLibraries();
        if (antLibraries == null || antLibraries.isEmpty()) {
            return "";
        }
        return String.join(separator, antLibraries);
    }

    /**
     * Convertește comenzi Windows la Unix.
     * Transformă "set VAR=val" → "export VAR=val".
     */
    private String convertToUnixScript(String command) {
        StringBuilder result = new StringBuilder();
        for (String line : command.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("set ")) {
                result.append("export ").append(trimmed.substring(4));
            } else {
                result.append(line);
            }
            result.append("\n");
        }
        return result.toString();
    }

    // ──────────────────────────────────────────────
    // Validation
    // ──────────────────────────────────────────────

    private java.util.Optional<BuildResult> validateNotEmpty(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            return java.util.Optional.of(BuildResult.failure(errorMessage));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<BuildResult> validateFileExists(String path, String errorMessage) {
        if (path != null && !new File(path).exists()) {
            return java.util.Optional.of(BuildResult.failure(errorMessage));
        }
        return java.util.Optional.empty();
    }

    // ──────────────────────────────────────────────
    // Logging
    // ──────────────────────────────────────────────

    private void logBuildInfo(File workingDir, String antPath) {
        log("✓ Build file: " + project.getBuildFilePath());
        log("✓ Ant target: " + project.getAntTarget());
        log("✓ Ant path: " + antPath);
        log("✓ Java 8 home: " + appConfig.getJava8Home());
        log("✓ Working directory: " + workingDir.getAbsolutePath());
    }

    private void logCommand(String command) {
        log("-------------------------------");
        log("▶ Command to execute:");
        for (String line : command.split("\\r?\\n")) {
            log("  " + line);
        }
        log("-------------------------------");
    }

    private void cleanupTempScript(File tempScript) {
        if (tempScript != null && tempScript.exists()) {
            try {
                Files.delete(tempScript.toPath());
                log("✓ Cleaned up temp script");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to delete temp script", e);
            }
        }
    }

    private void log(String message) {
        Platform.runLater(() -> logger.accept(message));
    }
}