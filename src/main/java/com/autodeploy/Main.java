package com.autodeploy;

import com.autodeploy.core.config.ThemeManager;
import com.autodeploy.ui.window.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    @Override
    public void start(Stage primaryStage) {
        try {
            ThemeManager.loadSavedTheme();

            MainWindow mainWindow = new MainWindow();
            mainWindow.show();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start application", e);
            throw e;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}