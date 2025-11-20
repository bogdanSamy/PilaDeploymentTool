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

package com.autodeploy.config;

import atlantafx.base.theme.*;
import javafx.application.Application;

/**
 * Utility class for managing application themes
 *
 * @author XDSSWAR
 * Created on 11/19/2025
 */
public class ThemeManager {

    /**
     * Available theme options
     */
    public enum Theme {
        PRIMER_LIGHT("Primer Light"),
        PRIMER_DARK("Primer Dark"),
        NORD_LIGHT("Nord Light"),
        NORD_DARK("Nord Dark"),
        CUPERTINO_LIGHT("Cupertino Light"),
        CUPERTINO_DARK("Cupertino Dark"),
        DRACULA("Dracula");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Theme fromDisplayName(String displayName) {
            for (Theme theme : values()) {
                if (theme.displayName.equals(displayName)) {
                    return theme;
                }
            }
            return PRIMER_LIGHT; // default
        }
    }

    /**
     * Apply theme to application
     *
     * @param themeName The display name of the theme to apply
     */
    public static void applyTheme(String themeName) {
        Theme theme = Theme.fromDisplayName(themeName);
        applyTheme(theme);
    }

    /**
     * Apply theme to application
     *
     * @param theme The theme to apply
     */
    public static void applyTheme(Theme theme) {
        String stylesheet = switch (theme) {
            case PRIMER_LIGHT -> new PrimerLight().getUserAgentStylesheet();
            case PRIMER_DARK -> new PrimerDark().getUserAgentStylesheet();
            case NORD_LIGHT -> new NordLight().getUserAgentStylesheet();
            case NORD_DARK -> new NordDark().getUserAgentStylesheet();
            case CUPERTINO_LIGHT -> new CupertinoLight().getUserAgentStylesheet();
            case CUPERTINO_DARK -> new CupertinoDark().getUserAgentStylesheet();
            case DRACULA -> new Dracula().getUserAgentStylesheet();
        };

        Application.setUserAgentStylesheet(stylesheet);
    }

    /**
     * Load and apply saved theme from configuration
     */
    public static void loadSavedTheme() {
        ApplicationConfig config = ApplicationConfig.getInstance();
        String savedTheme = config.getTheme();
        applyTheme(savedTheme);
    }

    /**
     * Get all available theme names
     *
     * @return Array of theme display names
     */
    public static String[] getAvailableThemes() {
        Theme[] themes = Theme.values();
        String[] themeNames = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            themeNames[i] = themes[i].getDisplayName();
        }
        return themeNames;
    }
}