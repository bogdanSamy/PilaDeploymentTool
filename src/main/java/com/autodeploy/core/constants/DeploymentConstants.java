package com.autodeploy.core.constants;

public final class DeploymentConstants {

    // Window
    public static final String WINDOW_TITLE_PREFIX = "Deployment Manager - ";
    public static final double TITLE_BAR_HEIGHT = 40.0;

    // SVG Shapes
    public static final String MIN_SHAPE = "M1 7L1 8L14 8L14 7Z";
    public static final String MAX_SHAPE = "M2.5 2 A 0.50005 0.50005 0 0 0 2 2.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L13.5 14 A 0.50005 0.50005 0 0 0 14 13.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L2.5 2 z M 3 3L13 3L13 13L3 13L3 3 z";
    public static final String REST_SHAPE = "M4.5 2 A 0.50005 0.50005 0 0 0 4 2.5L4 4L2.5 4 A 0.50005 0.50005 0 0 0 2 4.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L11.5 14 A 0.50005 0.50005 0 0 0 12 13.5L12 12L13.5 12 A 0.50005 0.50005 0 0 0 14 11.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L4.5 2 z M 5 3L13 3L13 11L12 11L12 4.5 A 0.50005 0.50005 0 0 0 11.5 4L5 4L5 3 z M 3 5L11 5L11 13L3 13L3 5 z";

    // File Extensions
    public static final String JAR_EXTENSION = ".jar";
    public static final String JSP_EXTENSION = ".jsp";

    // Messages
    public static final String MSG_NO_JAR_FILES = "No JAR files found";
    public static final String MSG_NO_JSP_FILES = "No JSP files found";
    public static final String MSG_NO_FILES_SELECTED = "Please select at least one file to upload.";
    public static final String MSG_NOT_CONNECTED = "Not connected to server. Please reconnect.";

    // Styles
    public static final String STYLE_CHECKBOX_DEFAULT = "-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-fg-default;";
    public static final String STYLE_CHECKBOX_HIGHLIGHTED = "-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-accent-emphasis; -fx-font-weight: bold;";
    public static final String STYLE_MUTED_TEXT = "-fx-text-fill: -color-fg-muted; -fx-padding: 10px;";

    // Timing
    public static final int RESTART_POLL_INTERVAL_MS = 2000;
    public static final int RECONNECT_DELAY_MS = 1000;
    public static final int TIMER_UPDATE_INTERVAL_MS = 100;

    // Indentation
    public static final int JSP_BASE_INDENT = 15;
    public static final int JSP_DEPTH_INDENT = 20;

    private DeploymentConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}