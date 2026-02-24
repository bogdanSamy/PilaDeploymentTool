package com.autodeploy.service.deploy;

/**
 * Rezultatul unui build sau al validării configurației de build.
 */
public class BuildResult {

    private final boolean success;
    private final int exitCode;
    private final String errorMessage;

    private BuildResult(boolean success, int exitCode, String errorMessage) {
        this.success = success;
        this.exitCode = exitCode;
        this.errorMessage = errorMessage;
    }

    public static BuildResult success(int exitCode) {
        return new BuildResult(true, exitCode, null);
    }

    public static BuildResult failure(String errorMessage) {
        return new BuildResult(false, -1, errorMessage);
    }

    public static BuildResult failure(int exitCode, String errorMessage) {
        return new BuildResult(false, exitCode, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public int getExitCode() { return exitCode; }
    public String getErrorMessage() { return errorMessage; }
}