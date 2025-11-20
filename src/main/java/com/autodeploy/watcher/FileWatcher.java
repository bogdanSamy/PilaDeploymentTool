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

package com.autodeploy.watcher;

import javafx.application.Platform;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * File Watcher utility for monitoring directory changes
 *
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class FileWatcher {

    private final Path directoryPath;
    private final String fileExtension;
    private final Consumer<FileChangeEvent> changeListener;
    private final Map<String, Long> fileTimestamps;
    private final boolean recursive;
    private Thread watchThread;
    private volatile boolean running = false;

    public FileWatcher(String directory, String extension, Consumer<FileChangeEvent> listener) {
        this(directory, extension, listener, false);
    }

    public FileWatcher(String directory, String extension, Consumer<FileChangeEvent> listener, boolean recursive) {
        this.directoryPath = Paths.get(directory);
        this.fileExtension = extension;
        this.changeListener = listener;
        this.fileTimestamps = new ConcurrentHashMap<>();
        this.recursive = recursive;
    }

    /**
     * Start watching the directory
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;

        // Initial scan
        scanDirectory();

        // Start watch thread
        watchThread = new Thread(this::watchLoop, "FileWatcher-" + fileExtension);
        watchThread.setDaemon(true);
        watchThread.start();

        System.out.println("✓ Started watching: " + directoryPath + " for *" + fileExtension + (recursive ? " (recursive)" : ""));
    }

    /**
     * Stop watching
     */
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        System.out.println("✓ Stopped watching: " + directoryPath);
    }

    /**
     * Initial directory scan
     */
    private void scanDirectory() {
        File directory = directoryPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("⚠ Directory not found: " + directoryPath);
            return;
        }

        if (recursive) {
            scanDirectoryRecursive(directory, "");
        } else {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(fileExtension));
            if (files != null) {
                for (File file : files) {
                    fileTimestamps.put(file.getName(), file.lastModified());
                }
            }
        }
    }

    /**
     * Scan directory recursively
     */
    private void scanDirectoryRecursive(File directory, String relativePath) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String newRelativePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                scanDirectoryRecursive(file, newRelativePath);
            } else if (file.getName().toLowerCase().endsWith(fileExtension)) {
                String fullPath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                fileTimestamps.put(fullPath, file.lastModified());
            }
        }
    }

    /**
     * Watch loop - checks for file changes every 2 seconds
     */
    private void watchLoop() {
        while (running) {
            try {
                checkForChanges();
                Thread.sleep(2000); // Check every 2 seconds
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("✗ Error in watch loop: " + e.getMessage());
            }
        }
    }

    /**
     * Check for file changes
     */
    private void checkForChanges() {
        File directory = directoryPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        Map<String, Long> currentFiles = new HashMap<>();

        if (recursive) {
            scanForChangesRecursive(directory, "", currentFiles);
        } else {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(fileExtension));
            if (files != null) {
                for (File file : files) {
                    currentFiles.put(file.getName(), file.lastModified());
                }
            }
        }

        // Check for new and modified files
        for (Map.Entry<String, Long> entry : currentFiles.entrySet()) {
            String fileName = entry.getKey();
            long lastModified = entry.getValue();

            Long previousTimestamp = fileTimestamps.get(fileName);

            if (previousTimestamp == null) {
                // New file added
                fileTimestamps.put(fileName, lastModified);
                File file = new File(directoryPath.toFile(), fileName);
                notifyChange(new FileChangeEvent(file, fileName, FileChangeType.ADDED));
            } else if (lastModified > previousTimestamp) {
                // File modified
                fileTimestamps.put(fileName, lastModified);
                File file = new File(directoryPath.toFile(), fileName);
                notifyChange(new FileChangeEvent(file, fileName, FileChangeType.MODIFIED));
            }
        }

        // Check for deleted files
        Set<String> deletedFiles = new HashSet<>(fileTimestamps.keySet());
        deletedFiles.removeAll(currentFiles.keySet());

        for (String deletedFile : deletedFiles) {
            fileTimestamps.remove(deletedFile);
            File file = new File(directoryPath.toFile(), deletedFile);
            notifyChange(new FileChangeEvent(file, deletedFile, FileChangeType.DELETED));
        }
    }

    /**
     * Scan for changes recursively
     */
    private void scanForChangesRecursive(File directory, String relativePath, Map<String, Long> currentFiles) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                String newRelativePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                scanForChangesRecursive(file, newRelativePath, currentFiles);
            } else if (file.getName().toLowerCase().endsWith(fileExtension)) {
                String fullPath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
                currentFiles.put(fullPath, file.lastModified());
            }
        }
    }

    /**
     * Notify listener on JavaFX thread
     */
    private void notifyChange(FileChangeEvent event) {
        Platform.runLater(() -> {
            try {
                changeListener.accept(event);
            } catch (Exception e) {
                System.err.println("✗ Error in change listener: " + e.getMessage());
            }
        });
    }

    /**
     * File change event
     */
    public static class FileChangeEvent {
        private final File file;
        private final String relativePath;
        private final FileChangeType type;

        public FileChangeEvent(File file, String relativePath, FileChangeType type) {
            this.file = file;
            this.relativePath = relativePath;
            this.type = type;
        }

        public File getFile() {
            return file;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public FileChangeType getType() {
            return type;
        }

        public String getFileName() {
            return file.getName();
        }
    }

    /**
     * File change type
     */
    public enum FileChangeType {
        ADDED, MODIFIED, DELETED
    }
}