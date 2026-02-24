package com.autodeploy.core.config;

import atlantafx.base.theme.*;
import javafx.application.Application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionare teme — AtlantaFX standard + teme custom (CSS override pe Primer Light).
 * Pentru a adăuga o temă nouă: adaugă în enum Theme + în CUSTOM_THEME_PATHS.
 */
public class ThemeManager {

    private static final Logger LOGGER = Logger.getLogger(ThemeManager.class.getName());

    private static final Map<Theme, String> CUSTOM_THEME_PATHS = Map.of(
            Theme.BABY_PINK, "/css/custom-themes/baby-pink.css",
            Theme.FOREST_GREEN, "/css/custom-themes/forest-green.css",
            Theme.AUTUMN_FOREST, "/css/custom-themes/autumn-forest.css",
            Theme.PEACH_ORANGE, "/css/custom-themes/peach-orange.css",
            Theme.OCEAN_BLUE, "/css/custom-themes/ocean-blue.css",
            Theme.LAVENDER_FIELDS, "/css/custom-themes/lavender-fields.css",
            Theme.CHRISTMAS, "/css/custom-themes/christmas.css"
    );

    private static final Map<Theme, Supplier<atlantafx.base.theme.Theme>> ATLANTAFX_THEMES = Map.of(
            Theme.PRIMER_LIGHT, PrimerLight::new,
            Theme.PRIMER_DARK, PrimerDark::new,
            Theme.NORD_LIGHT, NordLight::new,
            Theme.NORD_DARK, NordDark::new,
            Theme.DRACULA, Dracula::new
    );

    private static final Supplier<atlantafx.base.theme.Theme> BASE_THEME = PrimerLight::new;

    private ThemeManager() {}

    public enum Theme {
        PRIMER_LIGHT("Primer Light"),
        PRIMER_DARK("Primer Dark"),
        NORD_LIGHT("Nord Light"),
        NORD_DARK("Nord Dark"),
        DRACULA("Dracula"),
        BABY_PINK("Baby Pink"),
        FOREST_GREEN("Forest Green"),
        AUTUMN_FOREST("Autumn Forest"),
        PEACH_ORANGE("Peach Orange"),
        OCEAN_BLUE("Ocean Blue"),
        LAVENDER_FIELDS("Lavender Fields"),
        CHRISTMAS("Christmas");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }

        public static Theme fromDisplayName(String displayName) {
            for (Theme theme : values()) {
                if (theme.displayName.equals(displayName)) {
                    return theme;
                }
            }
            return PRIMER_LIGHT;
        }
    }

    public static void applyTheme(String themeName) {
        applyTheme(Theme.fromDisplayName(themeName));
    }

    public static void applyTheme(Theme theme) {
        String stylesheet = CUSTOM_THEME_PATHS.containsKey(theme)
                ? buildCustomStylesheet(theme)
                : getAtlantaFXStylesheet(theme);

        if (stylesheet == null || stylesheet.isEmpty()) {
            LOGGER.warning("Could not resolve stylesheet for theme: " + theme);
            return;
        }

        Application.setUserAgentStylesheet(stylesheet);
    }

    public static void loadSavedTheme() {
        applyTheme(ApplicationConfig.getInstance().getTheme());
    }

    public static String[] getAvailableThemes() {
        return Arrays.stream(Theme.values())
                .map(Theme::getDisplayName)
                .toArray(String[]::new);
    }

    /**
     * Construiește un stylesheet compozit: tema de bază (Primer Light) + override-ul CSS custom.
     * Rezultatul e encodat Base64 într-un data-URI, deoarece JavaFX nu suportă
     * încărcarea a două stylesheet-uri ca user-agent theme simultan.
     */
    private static String buildCustomStylesheet(Theme theme) {
        String baseThemeUrl = BASE_THEME.get().getUserAgentStylesheet();
        String customCss = loadResourceContent(CUSTOM_THEME_PATHS.get(theme));

        String combined = "@import url(\"" + baseThemeUrl + "\");\n" + customCss;

        return "data:text/css;base64," +
                Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    private static String getAtlantaFXStylesheet(Theme theme) {
        return Optional.ofNullable(ATLANTAFX_THEMES.get(theme))
                .map(supplier -> supplier.get().getUserAgentStylesheet())
                .orElseGet(() -> BASE_THEME.get().getUserAgentStylesheet());
    }

    private static String loadResourceContent(String path) {
        try (InputStream is = ThemeManager.class.getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.warning("Theme resource not found: " + path);
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load theme resource: " + path, e);
            return "";
        }
    }
}