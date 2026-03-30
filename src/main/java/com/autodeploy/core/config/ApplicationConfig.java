package com.autodeploy.core.config;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configurația aplicației, persistată în app-config.properties.
 * Singleton — acces prin getInstance().
 */
public class ApplicationConfig {

    private static final Logger LOGGER = Logger.getLogger(ApplicationConfig.class.getName());

    private static ApplicationConfig instance;
    private static final String CONFIG_FILE = "app-config.properties";

    private final Properties properties;

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
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            LOGGER.info("No config file found. Using defaults.");
            return;
        }

        try (InputStream input = new FileInputStream(file)) {
            properties.load(input);
            LOGGER.info("Loaded configuration from " + CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load " + CONFIG_FILE, e);
        }
    }

    public void save() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            properties.store(output, "Application Configuration");
            LOGGER.info("Saved configuration to " + CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not save " + CONFIG_FILE, e);
        }
    }

    public String getAntPath() {
        return properties.getProperty("ant.path", "");
    }

    public void setAntPath(String path) {
        properties.setProperty("ant.path", path);
    }

    public String getLocalDownloadDir() {
        return properties.getProperty("download.local.dir",
                System.getProperty("user.home") + "/Downloads");
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
        return properties.getProperty("username", "");
    }

    public void setUsername(String username) {
        properties.setProperty("username", username);
    }

    public String getTheme() {
        return properties.getProperty("app.theme", "Primer Light");
    }

    public void setTheme(String theme) {
        properties.setProperty("app.theme", theme);
    }

    public String getJava8Home() {
        return properties.getProperty("java8.home", "");
    }

    public void setJava8Home(String path) {
        properties.setProperty("java8.home", path);
    }
}