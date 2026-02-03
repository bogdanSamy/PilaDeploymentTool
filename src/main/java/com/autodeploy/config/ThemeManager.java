package com.autodeploy.config;
import atlantafx.base.theme.*;
import javafx.application.Application;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ThemeManager {

    public enum Theme {
        PRIMER_LIGHT("Primer Light"),
        PRIMER_DARK("Primer Dark"),
        NORD_LIGHT("Nord Light"),
        NORD_DARK("Nord Dark"),
        CUPERTINO_LIGHT("Cupertino Light"),
        CUPERTINO_DARK("Cupertino Dark"),
        DRACULA("Dracula"),
        BABY_PINK("Baby Pink");

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

    public static void applyTheme(String themeName) {
        Theme theme = Theme.fromDisplayName(themeName);
        applyTheme(theme);
    }

    public static void applyTheme(Theme theme) {
        if (theme == Theme.BABY_PINK) {
            // Logica specială pentru tema Custom
            // 1. Luăm URL-ul temei de bază (folosim Primer Light ca fundație)
            String baseThemeUrl = new PrimerLight().getUserAgentStylesheet();

            // 2. Citim conținutul CSS-ului nostru local (override-urile)
            String customCssContent = loadResourceContent("/css/custom-themes/baby-pink.css");

            // 3. Creăm un CSS virtual care le combină
            // Folosim @import url("...") pentru a încărca tema AtlantaFX corect
            String virtualCss = "@import url(\"" + baseThemeUrl + "\");\n" + customCssContent;

            // 4. Convertim în Data URI (base64) pentru ca JavaFX să îl accepte ca "fișier"
            String dataUri = "data:text/css;base64," +
                    Base64.getEncoder().encodeToString(virtualCss.getBytes(StandardCharsets.UTF_8));

            // 5. Aplicăm GLOBAL
            Application.setUserAgentStylesheet(dataUri);

        } else {
            // Logica standard pentru temele AtlantaFX
            String stylesheet = switch (theme) {
                case PRIMER_LIGHT -> new PrimerLight().getUserAgentStylesheet();
                case PRIMER_DARK -> new PrimerDark().getUserAgentStylesheet();
                case NORD_LIGHT -> new NordLight().getUserAgentStylesheet();
                case NORD_DARK -> new NordDark().getUserAgentStylesheet();
                case CUPERTINO_LIGHT -> new CupertinoLight().getUserAgentStylesheet();
                case CUPERTINO_DARK -> new CupertinoDark().getUserAgentStylesheet();
                case DRACULA -> new Dracula().getUserAgentStylesheet();
                default -> new PrimerLight().getUserAgentStylesheet();
            };
            Application.setUserAgentStylesheet(stylesheet);
        }
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

    public static void loadSavedTheme() {
        ApplicationConfig config = ApplicationConfig.getInstance();
        String savedTheme = config.getTheme();
        applyTheme(savedTheme);
    }

    public static String[] getAvailableThemes() {
        Theme[] themes = Theme.values();
        String[] themeNames = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            themeNames[i] = themes[i].getDisplayName();
        }
        return themeNames;
    }
}