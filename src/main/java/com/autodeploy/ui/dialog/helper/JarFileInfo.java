package com.autodeploy.ui.dialog.helper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * DTO pentru afișarea informațiilor despre un JAR în tabelul de rezultate build.
 * Valorile sunt pre-formatate la construcție (name, size, date) pentru binding direct în TableView.
 */
public class JarFileInfo {
    private final String name;
    private final long size;
    private final String sizeFormatted;
    private final String dateModified;

    public JarFileInfo(File file) {
        this.name = file.getName();
        this.size = file.length();
        this.sizeFormatted = formatSize(file.length());
        this.dateModified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new Date(file.lastModified()));
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public String getSizeFormatted() { return sizeFormatted; }
    public String getDateModified() { return dateModified; }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}