package com.autodeploy.service.scanner;

import com.autodeploy.domain.model.Project;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Scanează directoarele locale ale unui proiect pentru fișiere JAR/JSP
 * și gestionează monitorizarea lor prin {@link FileWatcher}.
 * <p>
 * Diferența de tratament între JAR și JSP:
 * <ul>
 *   <li><b>JAR:</b> scan non-recursiv — toate JAR-urile sunt în directorul root,
 *       returnează doar numele fișierului (ex: "mylib-1.0.jar")</li>
 *   <li><b>JSP:</b> scan recursiv — structura de foldere e semnificativă,
 *       returnează căi relative (ex: "pages/admin/index.jsp")</li>
 * </ul>
 * Căile relative sunt importante deoarece sunt folosite de {@link com.autodeploy.service.deploy.FileUploadService}
 * pentru a recrea aceeași structură de directoare pe server.
 */
public class FileScannerService {

    private static final Logger LOGGER = Logger.getLogger(FileScannerService.class.getName());

    private final Project project;
    private final Consumer<String> logger;

    private FileWatcher jarWatcher;
    private FileWatcher jspWatcher;

    public FileScannerService(Project project, Consumer<String> logger) {
        this.project = project;
        this.logger = logger;
    }

    public List<String> scanJarFiles() {
        return scanForFiles(project.getLocalJarPath(), JAR_EXTENSION).stream()
                .map(File::getName)
                .toList();
    }

    /**
     * Scanează recursiv directorul JSP și returnează căi relative la directorul root.
     * Căile relative păstrează structura de foldere necesară la upload.
     */
    public List<String> scanJspFiles() {
        return scanForFilesRecursive(project.getLocalJspPath(), JSP_EXTENSION).stream()
                .map(fwp -> fwp.relativePath)
                .toList();
    }

    public void startJarWatcher(Consumer<FileWatcher.FileChangeEvent> changeHandler) {
        if (project.getLocalJarPath() == null || project.getLocalJarPath().isEmpty()) return;

        jarWatcher = new FileWatcher(
                project.getLocalJarPath(), JAR_EXTENSION, changeHandler, false);
        jarWatcher.start();
        log("👁 Watching JAR directory: " + project.getLocalJarPath());
    }

    public void startJspWatcher(Consumer<FileWatcher.FileChangeEvent> changeHandler) {
        if (project.getLocalJspPath() == null || project.getLocalJspPath().isEmpty()) return;

        jspWatcher = new FileWatcher(
                project.getLocalJspPath(), JSP_EXTENSION, changeHandler, true);
        jspWatcher.start();
        log("👁 Watching JSP directory (recursive): " + project.getLocalJspPath());
    }

    public void stopWatchers() {
        if (jarWatcher != null) { jarWatcher.stop(); jarWatcher = null; }
        if (jspWatcher != null) { jspWatcher.stop(); jspWatcher = null; }
        log("✓ Stopped file watchers");
    }

    /**
     * Scan non-recursiv — listează fișierele cu extensia dată dintr-un singur director.
     * Folosit pentru JAR-uri (structură plată).
     */
    private List<File> scanForFiles(String directoryPath, String extension) {
        if (directoryPath == null || directoryPath.isEmpty()) return List.of();

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log("⚠ Directory not found: " + directoryPath);
            return List.of();
        }

        File[] found = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(extension));
        if (found == null) return List.of();

        List<File> files = new ArrayList<>(Arrays.asList(found));
        files.sort(Comparator.comparing(File::getName));
        return files;
    }

    /**
     * Scan recursiv — parcurge toată arborescența de foldere.
     * Folosit pentru JSP-uri unde structura de directoare trebuie păstrată.
     * Returnează {@link FileWithPath} care conține atât File-ul cât și calea relativă.
     */
    private List<FileWithPath> scanForFilesRecursive(String directoryPath, String extension) {
        if (directoryPath == null || directoryPath.isEmpty()) return List.of();

        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            log("⚠ Directory not found: " + directoryPath);
            return List.of();
        }

        List<FileWithPath> files = new ArrayList<>();
        scanDirectoryRecursive(directory, "", extension, files);
        files.sort(Comparator.comparing(f -> f.relativePath));
        return files;
    }

    /**
     * Parcurgere recursivă DFS — construiește calea relativă incremential
     * pe măsură ce coboară în arborescență (ex: "" → "pages" → "pages/admin").
     */
    private void scanDirectoryRecursive(File directory, String relativePath,
                                        String extension, List<FileWithPath> result) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            String path = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, path, extension, result);
            } else if (file.getName().toLowerCase().endsWith(extension)) {
                result.add(new FileWithPath(file, path));
            }
        }
    }

    private void log(String message) {
        if (logger != null) logger.accept(message);
    }

    /** Pereche File + cale relativă la directorul root al scanării. */
    public static class FileWithPath {
        public final File file;
        public final String relativePath;

        public FileWithPath(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }
    }
}