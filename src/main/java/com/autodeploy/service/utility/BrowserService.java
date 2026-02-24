package com.autodeploy.service.utility;

import com.autodeploy.core.config.ApplicationConfig;
import com.autodeploy.domain.model.Server;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deschide URL-uri în browser-ul default al sistemului.
 * <p>
 * Strategie: încearcă mai întâi {@link Desktop#browse}, și dacă nu e suportat
 * (headless, unele distribuții Linux), face fallback pe comanda OS nativă
 * (rundll32/open/xdg-open).
 */
public class BrowserService {

    private static final Logger LOGGER = Logger.getLogger(BrowserService.class.getName());

    private final Consumer<String> logger;
    private final ApplicationConfig appConfig;

    public BrowserService(Consumer<String> logger) {
        this.logger = logger;
        this.appConfig = ApplicationConfig.getInstance();
    }

    /**
     * Deschide URL-ul complet al serverului (IP + suffix configurat) în browser.
     */
    public boolean openServer(Server server) {
        log("🌐 Opening server in browser...");
        String url = appConfig.getFullBrowserUrl(server.getHost());
        log("✓ URL: " + url);
        return openUrl(url);
    }

    /**
     * Deschide un URL în browser-ul default. Fallback pe comandă OS nativă
     * dacă {@link Desktop#browse} nu e disponibil sau eșuează.
     */
    public boolean openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log("✓ Browser opened successfully");
                return true;
            }

            log("✗ Desktop browse not supported, trying fallback...");
            return openUrlFallback(url);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open browser", e);
            log("✗ Failed to open browser: " + e.getMessage());
            return openUrlFallback(url);
        }
    }

    /**
     * Fallback: deschide URL-ul prin comanda nativă a OS-ului.
     * Windows: rundll32, macOS: open, Linux: xdg-open.
     */
    private boolean openUrlFallback(String url) {
        try {
            ProcessBuilder pb;

            if (OsHelper.isWindows()) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (OsHelper.isMac()) {
                pb = new ProcessBuilder("open", url);
            } else if (OsHelper.isLinux()) {
                pb = new ProcessBuilder("xdg-open", url);
            } else {
                log("✗ Unsupported operating system");
                return false;
            }

            pb.start();
            log("✓ Browser opened via system command");
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Fallback browser open failed", e);
            log("✗ Fallback browser open failed: " + e.getMessage());
            return false;
        }
    }

    private void log(String message) {
        logger.accept(message);
    }
}