package com.autodeploy.ui.tab;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Parent;

import java.util.UUID;

/**
 * Clasa de bază abstractă pentru un tab de sesiune în fereastra principală.
 * <p>
 * Fiecare tab reprezintă o sesiune independentă (selecție server/proiect
 * sau un deployment activ). Tab-urile sunt găzduite de {@link com.autodeploy.ui.window.MainWindow}
 * și gestionate de {@link SessionTabManager}.
 * <p>
 * Subclase:
 * <ul>
 *   <li>{@link SelectionSessionTab} — ecranul de selecție server/proiect</li>
 *   <li>{@link DeploymentSessionTab} — deployment activ pentru un Project + Server</li>
 * </ul>
 */
public abstract class SessionTab {

    protected final UUID id = UUID.randomUUID();
    protected final StringProperty displayName = new SimpleStringProperty("Tab");
    /** Nodul JavaFX embeddabil care reprezintă conținutul acestui tab. */
    protected Parent contentRoot;

    public UUID getId() {
        return id;
    }

    public StringProperty displayNameProperty() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName.get();
    }

    public void setDisplayName(String name) {
        displayName.set(name);
    }

    public Parent getContentRoot() {
        return contentRoot;
    }

    /**
     * Curăță resursele acestui tab (conexiuni, file watchers, polling).
     * Apelat la închiderea tab-ului sau la ieșirea din aplicație.
     */
    public abstract void close();
}
