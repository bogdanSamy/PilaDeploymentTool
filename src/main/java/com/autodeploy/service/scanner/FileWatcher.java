package com.autodeploy.service.scanner;

import javafx.application.Platform;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitorizează un director pentru fișiere cu o anumită extensie prin polling.
 * Detectează fișiere noi (ADDED), modificate (MODIFIED) și șterse (DELETED).
 * <p>
 * Implementare bazată pe polling (comparare timestamp-uri) în loc de
 * {@code java.nio.file.WatchService} deoarece:
 * <ul>
 *   <li>WatchService nu funcționează reliable pe network drives (NFS, SMB)</li>
 *   <li>WatchService pe macOS folosește polling oricum (fără kqueue support nativ)</li>
 *   <li>Polling la 2s e suficient de responsive pentru un workflow de development</li>
 * </ul>
 * <p>
 * Notificările sunt livrate pe JavaFX Application Thread prin {@code Platform.runLater()}.
 * <p>
 * <b>Thread safety:</b> {@code fileTimestamps} e ConcurrentHashMap — safe pentru
 * citire din UI thread și scriere din watch thread.
 */
public class FileWatcher {

    private static final Logger LOGGER = Logger.getLogger(FileWatcher.class.getName());
    private static final int DEFAULT_POLL_INTERVAL_MS = 2000;

    private final Path directoryPath;
    private final String fileExtension;
    private final Consumer<FileChangeEvent> changeListener;
    private final boolean recursive;
    private final int pollIntervalMs;

    /**
     * Snapshot-ul curent al fișierelor monitorizate: cale relativă → lastModified.
     * Folosit ca bază de comparație la fiecare poll cycle.
     */
    private final Map<String, Long> fileTimestamps = new ConcurrentHashMap<>();
    private Thread watchThread;
    private volatile boolean running = false;

    public FileWatcher(String directory, String extension,
                       Consumer<FileChangeEvent> listener, boolean recursive) {
        this(directory, extension, listener, recursive, DEFAULT_POLL_INTERVAL_MS);
    }

    public FileWatcher(String directory, String extension,
                       Consumer<FileChangeEvent> listener, boolean recursive,
                       int pollIntervalMs) {
        this.directoryPath = Paths.get(directory);
        this.fileExtension = extension;
        this.changeListener = listener;
        this.recursive = recursive;
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * Pornește monitoring-ul. Scan-ul inițial populează snapshot-ul
     * fără a genera notificări (fișierele existente nu sunt "noi").
     */
    public void start() {
        if (running) return;
        running = true;

        collectFiles(directoryPath.toFile(), "").forEach(
                entry -> fileTimestamps.put(entry.getKey(), entry.getValue())
        );

        watchThread = new Thread(this::watchLoop,
                "FileWatcher-" + fileExtension + "-" + directoryPath.getFileName());
        watchThread.setDaemon(true);
        watchThread.start();

        LOGGER.info("Started watching: " + directoryPath + " for *" + fileExtension
                + (recursive ? " (recursive)" : ""));
    }

    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        LOGGER.info("Stopped watching: " + directoryPath);
    }

    private void watchLoop() {
        while (running) {
            try {
                detectChanges();
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in watch loop", e);
            }
        }
    }

    /**
     * Compară starea curentă a filesystem-ului cu snapshot-ul anterior.
     * <p>
     * Algoritmul în 3 pași:
     * <ol>
     *   <li>Scanează starea curentă → {@code currentFiles}</li>
     *   <li>Compară cu {@code fileTimestamps}: fișierele noi (absent din snapshot)
     *       → ADDED, fișierele cu timestamp mai mare → MODIFIED</li>
     *   <li>Fișierele prezente în snapshot dar absente din currentFiles → DELETED</li>
     * </ol>
     */
    private void detectChanges() {
        File directory = directoryPath.toFile();
        if (!directory.exists() || !directory.isDirectory()) return;

        Map<String, Long> currentFiles = new HashMap<>();
        collectFiles(directory, "").forEach(
                entry -> currentFiles.put(entry.getKey(), entry.getValue())
        );

        for (Map.Entry<String, Long> entry : currentFiles.entrySet()) {
            String fileName = entry.getKey();
            long lastModified = entry.getValue();
            Long previousTimestamp = fileTimestamps.get(fileName);

            if (previousTimestamp == null) {
                fileTimestamps.put(fileName, lastModified);
                notifyChange(fileName, FileChangeType.ADDED);
            } else if (lastModified > previousTimestamp) {
                fileTimestamps.put(fileName, lastModified);
                notifyChange(fileName, FileChangeType.MODIFIED);
            }
        }

        Set<String> deleted = new HashSet<>(fileTimestamps.keySet());
        deleted.removeAll(currentFiles.keySet());

        for (String deletedFile : deleted) {
            fileTimestamps.remove(deletedFile);
            notifyChange(deletedFile, FileChangeType.DELETED);
        }
    }

    /**
     * Colectează recursiv (dacă {@code recursive=true}) toate fișierele cu extensia potrivită.
     * Returnează perechi (relativePath → lastModified) pentru comparare cu snapshot-ul.
     */
    private List<Map.Entry<String, Long>> collectFiles(File directory, String relativePath) {
        List<Map.Entry<String, Long>> result = new ArrayList<>();

        File[] files = directory.listFiles();
        if (files == null) return result;

        for (File file : files) {
            String path = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();

            if (file.isDirectory() && recursive) {
                result.addAll(collectFiles(file, path));
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(fileExtension)) {
                result.add(Map.entry(path, file.lastModified()));
            }
        }

        return result;
    }

    private void notifyChange(String relativePath, FileChangeType type) {
        Platform.runLater(() -> {
            try {
                changeListener.accept(new FileChangeEvent(relativePath, type));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in change listener", e);
            }
        });
    }

    public enum FileChangeType {
        ADDED, MODIFIED, DELETED
    }

    public static class FileChangeEvent {
        private final String relativePath;
        private final FileChangeType type;

        public FileChangeEvent(String relativePath, FileChangeType type) {
            this.relativePath = relativePath;
            this.type = type;
        }

        public String getRelativePath() { return relativePath; }
        public FileChangeType getType() { return type; }
    }
}