package com.autodeploy.services;

import com.autodeploy.config.ApplicationConfig;
import com.autodeploy.model.Server;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for opening URLs in system browser
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
     * Open server in browser
     */
    public boolean openServer(Server server) {
        log("🌐 Opening server in browser...");

        String url = appConfig.getFullBrowserUrl(server.getHost());
        log("✓ URL: " + url);

        return openUrl(url);
    }

    /**
     * Open URL in browser
     */
    public boolean openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log("✓ Browser opened successfully");
                return true;
            } else {
                log("✗ Desktop browse is not supported on this platform");
                return openUrlFallback(url);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to open browser", e);
            log("✗ Failed to open browser: " + e.getMessage());
            return openUrlFallback(url);
        }
    }

    /**
     * Fallback method using command line
     */
    private boolean openUrlFallback(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
                log("✓ Browser opened via Windows command");
                return true;

            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec("open " + url);
                log("✓ Browser opened via macOS command");
                return true;

            } else if (os.contains("nix") || os.contains("nux")) {
                Runtime.getRuntime().exec("xdg-open " + url);
                log("✓ Browser opened via Linux command");
                return true;

            } else {
                log("✗ Unsupported operating system: " + os);
                return false;
            }

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Fallback browser open failed", e);
            log("✗ Fallback browser open failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Log message
     */
    private void log(String message) {
        logger.accept(message);
    }
}