package com.autodeploy.core.assets;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Punct central de acces la resursele aplicației (FXML, CSS, imagini).
 * Toate căile sunt relative la classpath-ul acestei clase.
 */
public class Assets {

    private Assets() {}

    public static URL location(final String path) {
        return Assets.class.getResource(path);
    }

    public static InputStream stream(final String path) {
        return Assets.class.getResourceAsStream(path);
    }

    /**
     * Încarcă un FXML și îi atașează controller-ul dat.
     * Controller-ul NU este definit în FXML (fx:controller), ci injectat programatic.
     */
    public static Parent loadFxml(String fxmlPath, Object controller) throws IOException {
        FXMLLoader loader = new FXMLLoader(location(fxmlPath));
        loader.setController(controller);
        return loader.load();
    }
}