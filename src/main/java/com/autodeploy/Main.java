package com.autodeploy;

import atlantafx.base.theme.*;
import com.autodeploy.config.ThemeManager;

import com.autodeploy.ui.SelectionWindow;
import com.autodeploy.ui.dialogs.NotificationController;
import javafx.application.Application;
import javafx.stage.Stage;
import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        ThemeManager.loadSavedTheme();

        SelectionWindow selectionWindow = new SelectionWindow();
        selectionWindow.show();

        //ProjectManagementDialog projectManagementDialog = new ProjectManagementDialog(false);
        //projectManagementDialog.show();

//        SettingsDialog settingsDialog = new SettingsDialog(false);
//        settingsDialog.show();

//        NfxDemoWindow demoWindow = new NfxDemoWindow();
//        demoWindow.show();

        // Creare și afișare notificare
//        NotificationController notification1 = new NotificationController();
//        notification1.showSimpleNotification("Info", "Aceasta este o notificare simplă");

        NotificationController notification3 = new NotificationController();
        notification3.showImportantNotification(
                "ATENȚIE CRITICĂ!",
                "Cineva vrea sa restarteze serverul!"
        );

    }

    public static void main(String[] args) {
        launch(args);
    }

}