package com.autodeploy.service.deploy;

/**
 * Rezultatul unui upload (JAR sau JSP).
 */
public class UploadResult {

    private final int successCount;
    private final int failCount;
    private final boolean connectionLost;

    public UploadResult(int successCount, int failCount, boolean connectionLost) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.connectionLost = connectionLost;
    }

    public int getSuccessCount() { return successCount; }
    public int getFailCount() { return failCount; }
    public boolean isConnectionLost() { return connectionLost; }
    public boolean hasFailures() { return failCount > 0; }
}