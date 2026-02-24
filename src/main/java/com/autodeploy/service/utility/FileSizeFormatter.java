package com.autodeploy.service.utility;

/**
 * Formatare dimensiune fișiere. Folosit de FileUploadService,
 * LogDownloadService, JarFileInfo.
 */
public final class FileSizeFormatter {

    private FileSizeFormatter() {}

    public static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}