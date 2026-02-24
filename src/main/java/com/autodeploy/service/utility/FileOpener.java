package com.autodeploy.service.utility;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitar pentru deschiderea fișierelor și folderelor cu aplicațiile sistemului.
 * Extras din LogDownloadService — reutilizabil oriunde.
 */
public class FileOpener {

    private static final Logger LOGGER = Logger.getLogger(FileOpener.class.getName());

    private final Consumer<String> logger;

    public FileOpener(Consumer<String> logger) {
        this.logger = logger;
    }

    /**
     * Deschide dialogul "Open With" al sistemului pentru un fișier.
     * <p>
     * Pe Windows folosește {@code OpenAs_RunDLL} care deschide selectorul de aplicații.
     * Pe macOS/Linux deschide cu aplicația default (nu există dialog "Open With" nativ din CLI).
     */
    public boolean openWithDialog(File file) {
        if (file == null || !file.exists()) {
            log("✗ File does not exist: " + file);
            return false;
        }

        try {
            ProcessBuilder pb;

            if (OsHelper.isWindows()) {
                pb = new ProcessBuilder("rundll32.exe", "shell32.dll,OpenAs_RunDLL",
                        file.getAbsolutePath());
            } else if (OsHelper.isMac()) {
                pb = new ProcessBuilder("open", "-a", "Finder", file.getAbsolutePath());
            } else if (OsHelper.isLinux()) {
                pb = new ProcessBuilder("xdg-open", file.getAbsolutePath());
            } else {
                log("✗ Unsupported operating system");
                return false;
            }

            pb.start();
            log("✓ 'Open With' dialog opened for: " + file.getName());
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to open 'Open With' dialog", e);
            log("✗ Failed to open 'Open With' dialog: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deschide folderul care conține fișierul.
     */
    public boolean openContainingFolder(File file) {
        if (file == null || !file.exists()) return false;

        try {
            Desktop.getDesktop().open(file.getParentFile());
            log("✓ Opened containing folder");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open containing folder", e);
            log("✗ Failed to open folder: " + e.getMessage());
            return false;
        }
    }

    private void log(String message) {
        if (logger != null) logger.accept(message);
    }
}