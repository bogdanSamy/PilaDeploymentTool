package com.autodeploy;

import atlantafx.base.theme.*;
import com.autodeploy.config.ThemeManager;
import com.autodeploy.ui.SelectionWindow;
import javafx.application.Application;
import javafx.stage.Stage;
import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {

        ThemeManager.loadSavedTheme();
        SelectionWindow selectionWindow = new SelectionWindow();
        selectionWindow.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}