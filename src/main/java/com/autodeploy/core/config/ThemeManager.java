package com.autodeploy.core.config;

import atlantafx.base.theme.*;
import javafx.application.Application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class ThemeManager {

    // =================================================================================
    // CONFIGURAȚIE - Adaugă temele noi DOAR aici!
    // =================================================================================

    /**
     * Mapare teme custom -> căi CSS.
     * Pentru o temă nouă, adaugă o intrare aici și în enum.
     */
    private static final Map<Theme, String> CUSTOM_THEME_PATHS = Map.of(
            Theme.BABY_PINK, "/css/custom-themes/baby-pink.css",
            Theme.FOREST_GREEN, "/css/custom-themes/forest-green.css",
            Theme.AUTUMN_FOREST, "/css/custom-themes/autumn-forest.css",
            Theme.PEACH_ORANGE, "/css/custom-themes/peach-orange.css",
            Theme.OCEAN_BLUE, "/css/custom-themes/ocean-blue.css",
            Theme.LAVENDER_FIELDS, "/css/custom-themes/lavender-fields.css",
            Theme.CHRISTMAS, "/css/custom-themes/christmas.css"
    );

    /**
     * Mapare teme AtlantaFX standard -> supplier-i pentru instanțe.
     */
    private static final Map<Theme, Supplier<atlantafx.base.theme.Theme>> ATLANTAFX_THEMES = Map.of(
            Theme.PRIMER_LIGHT, PrimerLight::new,
            Theme.PRIMER_DARK, PrimerDark::new,
            Theme.NORD_LIGHT, NordLight::new,
            Theme.NORD_DARK, NordDark::new,
            Theme.DRACULA, Dracula::new
    );

    /**
     * Tema de bază pentru override-urile custom.
     */
    private static final Supplier<atlantafx.base.theme.Theme> BASE_THEME = PrimerLight::new;

    // =================================================================================
    // ENUM THEME
    // =================================================================================

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

        public String getDisplayName() {
            return displayName;
        }

        public static Theme fromDisplayName(String displayName) {
            for (Theme theme : values()) {
                if (theme.displayName.equals(displayName)) {
                    return theme;
                }
            }
            return PRIMER_LIGHT;
        }
    }

    // =================================================================================
    // METODE PUBLICE
    // =================================================================================

    public static void applyTheme(String themeName) {
        applyTheme(Theme.fromDisplayName(themeName));
    }

    public static void applyTheme(Theme theme) {
        String stylesheet = CUSTOM_THEME_PATHS.containsKey(theme)
                ? buildCustomStylesheet(theme)
                : getAtlantaFXStylesheet(theme);

        Application.setUserAgentStylesheet(stylesheet);
    }

    public static void loadSavedTheme() {
        ApplicationConfig config = ApplicationConfig.getInstance();
        applyTheme(config.getTheme());
    }

    public static String[] getAvailableThemes() {
        Theme[] themes = Theme.values();
        String[] themeNames = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            themeNames[i] = themes[i].getDisplayName();
        }
        return themeNames;
    }

    // =================================================================================
    // METODE PRIVATE HELPER
    // =================================================================================

    /**
     * Construiește stylesheet-ul pentru o temă custom (base + override).
     */
    private static String buildCustomStylesheet(Theme theme) {
        String baseThemeUrl = BASE_THEME.get().getUserAgentStylesheet();
        String customCssContent = loadResourceContent(CUSTOM_THEME_PATHS.get(theme));

        String combinedCss = "@import url(\"" + baseThemeUrl + "\");\n" + customCssContent;

        return "data:text/css;base64," +
                Base64.getEncoder().encodeToString(combinedCss.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Obține stylesheet-ul pentru o temă AtlantaFX standard.
     */
    private static String getAtlantaFXStylesheet(Theme theme) {
        return Optional.ofNullable(ATLANTAFX_THEMES.get(theme))
                .map(supplier -> supplier.get().getUserAgentStylesheet())
                .orElseGet(() -> BASE_THEME.get().getUserAgentStylesheet());
    }

    private static String loadResourceContent(String path) {
        try (InputStream is = ThemeManager.class.getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("Nu s-a putut găsi fișierul de resurse: " + path);
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }
}