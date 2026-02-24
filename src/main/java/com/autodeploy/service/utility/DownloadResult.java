package com.autodeploy.service.utility;

import java.io.File;

/**
 * Rezultatul unui download sau al validării configurației de download.
 */
public class DownloadResult {

    private final boolean success;
    private final File downloadedFile;
    private final String errorMessage;

    private DownloadResult(boolean success, File downloadedFile, String errorMessage) {
        this.success = success;
        this.downloadedFile = downloadedFile;
        this.errorMessage = errorMessage;
    }

    public static DownloadResult success(File downloadedFile) {
        return new DownloadResult(true, downloadedFile, null);
    }

    public static DownloadResult failure(String errorMessage) {
        return new DownloadResult(false, null, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public File getDownloadedFile() { return downloadedFile; }
    public String getErrorMessage() { return errorMessage; }
}