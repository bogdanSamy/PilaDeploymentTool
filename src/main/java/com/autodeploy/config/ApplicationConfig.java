package com.autodeploy.config;

import java.io.*;
import java.util.Properties;

public class ApplicationConfig {
    private static ApplicationConfig instance;
    private static final String CONFIG_FILE = "app-config.properties";

    private Properties properties;

    private ApplicationConfig() {
        properties = new Properties();
        load();
    }

    public static synchronized ApplicationConfig getInstance() {
        if (instance == null) {
            instance = new ApplicationConfig();
        }
        return instance;
    }

    private void load() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Could not load app-config.properties: " + e.getMessage());
        }
    }

    public void save() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            properties.store(output, "Application Configuration");
        } catch (IOException e) {
            System.err.println("Could not save app-config.properties: " + e.getMessage());
        }
    }

    public String getAntPath() {
        return properties.getProperty("ant.path", "");
    }

    public void setAntPath(String path) {
        properties.setProperty("ant.path", path);
    }

    public String getBrowserUrlSuffix() {
        return properties.getProperty("browser.url.suffix", "");
    }

    public void setBrowserUrlSuffix(String suffix) {
        properties.setProperty("browser.url.suffix", suffix);
    }

    public String getLocalDownloadDir() {
        return properties.getProperty("download.local.dir", System.getProperty("user.home") + "/Downloads");
    }

    public void setLocalDownloadDir(String dir) {
        properties.setProperty("download.local.dir", dir);
    }

    public String getRemoteLogPath() {
        return properties.getProperty("download.remote.log.path", "");
    }

    public void setRemoteLogPath(String path) {
        properties.setProperty("download.remote.log.path", path);
    }

    public String getUsername() {
        return properties.getProperty("username", "null");
    }

    public void setUsername(String path) {
        properties.setProperty("username", path);
    }

    // NEW: Theme configuration
    public String getTheme() {
        return properties.getProperty("app.theme", "Primer Light");
    }

    public void setTheme(String theme) {
        properties.setProperty("app.theme", theme);
    }

    // NEW: Build URL with IP and suffix
    public String getFullBrowserUrl(String serverIp) {
        String suffix = getBrowserUrlSuffix();
        if (suffix == null || suffix.trim().isEmpty()) {
            return "http://" + serverIp;
        }

        // Ensure suffix starts with /
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }

        return "http://" + serverIp + suffix;
    }
}