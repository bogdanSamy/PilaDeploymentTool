package com.autodeploy.core.constants;

public final class Constants {

    private Constants() {}

    // WINDOW
    public static final String WINDOW_TITLE = "La Pila & La Ciocan";
    public static final String WINDOW_TITLE_PREFIX = "Deployment Manager - ";
    public static final double TITLE_BAR_HEIGHT = 40.0;

    // Window control SVG shapes
    public static final String MIN_SHAPE = "M1 7L1 8L14 8L14 7Z";
    public static final String MAX_SHAPE = "M2.5 2 A 0.50005 0.50005 0 0 0 2 2.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L13.5 14 A 0.50005 0.50005 0 0 0 14 13.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L2.5 2 z M 3 3L13 3L13 13L3 13L3 3 z";
    public static final String REST_SHAPE = "M4.5 2 A 0.50005 0.50005 0 0 0 4 2.5L4 4L2.5 4 A 0.50005 0.50005 0 0 0 2 4.5L2 13.5 A 0.50005 0.50005 0 0 0 2.5 14L11.5 14 A 0.50005 0.50005 0 0 0 12 13.5L12 12L13.5 12 A 0.50005 0.50005 0 0 0 14 11.5L14 2.5 A 0.50005 0.50005 0 0 0 13.5 2L4.5 2 z M 5 3L13 3L13 11L12 11L12 4.5 A 0.50005 0.50005 0 0 0 11.5 4L5 4L5 3 z M 3 5L11 5L11 13L3 13L3 5 z";
    public static final String CLOSE_SHAPE = "M3.726563 3.023438L3.023438 3.726563L7.292969 8L3.023438 12.269531L3.726563 12.980469L8 8.707031L12.269531 12.980469L12.980469 12.269531L8.707031 8L12.980469 3.726563L12.269531 3.023438L8 7.292969Z";

    // SELECTION WINDOW
    public static final String SERVER_PROMPT = "Select a server...";
    public static final String PROJECT_PROMPT = "Select a project...";

    // DEPLOYMENT WINDOW
    public static final String MSG_NO_FILES_SELECTED = "Please select at least one file to upload.";
    public static final String MSG_NOT_CONNECTED = "Not connected to server. Please reconnect.";
    public static final int TIMER_UPDATE_INTERVAL_MS = 100;

    // STYLES
    public static final String STYLE_CHECKBOX_DEFAULT =
            "-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-fg-default;";
    public static final String STYLE_CHECKBOX_HIGHLIGHTED =
            "-fx-font-size: 13px; -fx-padding: 5px; -fx-text-fill: -color-accent-emphasis; -fx-font-weight: bold;";
    public static final String STYLE_MUTED_TEXT =
            "-fx-text-fill: -color-fg-muted; -fx-padding: 10px;";

    // FILE SCANNER
    public static final String JAR_EXTENSION = ".jar";
    public static final String JSP_EXTENSION = ".jsp";
    public static final String MSG_NO_JAR_FILES = "No JAR files found";
    public static final String MSG_NO_JSP_FILES = "No JSP files found";

    // ALERT ICONS (SVG paths)
    public static final String ERROR_ICON = "M8 0C3.6 0 0 3.6 0 8s3.6 8 8 8 8-3.6 8-8-3.6-8-8-8zm1 12H7V7h2v5zm0-6H7V4h2v2z";
    public static final String INFO_ICON = "M8 0C3.6 0 0 3.6 0 8s3.6 8 8 8 8-3.6 8-8-3.6-8-8-8zm1 12H7V7h2v5zm0-6H7V4h2v2z";
    public static final String WARNING_ICON = "M8.9 1.5C8.7 1.2 8.4 1 8 1s-.7.2-.9.5l-7 12c-.2.3-.2.7 0 1 .2.3.6.5 1 .5h14c.4 0 .8-.2 1-.5.2-.3.2-.7 0-1l-7-12zM9 13H7v-2h2v2zm0-3H7V6h2v4z";
    public static final String QUESTION_ICON = "M8 0C3.6 0 0 3.6 0 8s3.6 8 8 8 8-3.6 8-8-3.6-8-8-8zm1 13H7v-2h2v2zm1.1-5.4c-.4.4-.8.7-.8 1.5H7.7c0-1.3.7-1.8 1.1-2.2.3-.3.5-.5.5-1 0-.8-.7-1.5-1.5-1.5S6.2 5.1 6.2 6H4.7c0-2 1.6-3.5 3.5-3.5S11.7 4 11.7 6c0 1-.5 1.6-1.6 2.6z";

    public static final int ALERT_TITLE_BAR_HEIGHT = 30;


    // VALIDATION MESSAGES
    public static final String VALIDATION_MESSAGE =
            "Please fill in all required fields:\n- Name\n- Host\n- Port\n- Username\n- Password\n- Restart Script Path";
}