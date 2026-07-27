package com.autodeploy.ui.tab;

import com.autodeploy.domain.model.Project;
import com.autodeploy.domain.model.Server;
import com.autodeploy.ui.window.MainWindow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Gestionează lista de tab-uri active din {@link MainWindow}.
 * <p>
 * Responsabilități:
 * <ul>
 *   <li>Menține lista observabilă de tab-uri deschise</li>
 *   <li>Urmărește tab-ul activ curent</li>
 *   <li>Creează și deschide tab-uri noi (selecție / deployment)</li>
 *   <li>Închide tab-uri cu cleanup complet al resurselor</li>
 *   <li>Sincronizează lista cu UI-ul din titlul ferestrei (via MainWindow)</li>
 * </ul>
 * <p>
 * Comportament la ultimul tab: apelul {@link #closeTab} pe ultimul tab
 * apelează {@link Platform#exit()} — comportament identic cu închiderea
 * singurului Stage în modelul anterior multi-fereastră.
 */
public class SessionTabManager {

    private static final Logger LOGGER = Logger.getLogger(SessionTabManager.class.getName());

    private final ObservableList<SessionTab> tabs = FXCollections.observableArrayList();
    private SessionTab activeTab;
    private final MainWindow mainWindow;

    public SessionTabManager(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
    }

    public ObservableList<SessionTab> getTabs() {
        return tabs;
    }

    public SessionTab getActiveTab() {
        return activeTab;
    }

    /** Deschide un nou tab de selecție server/proiect și îl activează. */
    public void openSelectionTab() {
        SelectionSessionTab tab = new SelectionSessionTab(this, mainWindow);
        addTab(tab);
        setActiveTab(tab);
    }

    /** Deschide un nou tab de deployment pentru combinația project+server dată și îl activează. */
    public void openDeploymentTab(Project project, Server server) {
        DeploymentSessionTab tab = new DeploymentSessionTab(project, server, this, mainWindow);
        addTab(tab);
        setActiveTab(tab);
    }

    /**
     * Închide un tab: curăță resursele, îl elimină din UI și activează un vecin.
     * Dacă e ultimul tab, apelează {@link Platform#exit()}.
     */
    public void closeTab(SessionTab tab) {
        tab.close();

        int index = tabs.indexOf(tab);
        tabs.remove(tab);
        mainWindow.removeTabContent(tab);
        mainWindow.removeTabChip(tab);

        if (tabs.isEmpty()) {
            Platform.exit();
        } else if (tab == activeTab) {
            int newIndex = Math.max(0, Math.min(index, tabs.size() - 1));
            setActiveTab(tabs.get(newIndex));
        }
    }

    /** Activează un tab existent: îl face vizibil și actualizează stilul chip-urilor. */
    public void setActiveTab(SessionTab tab) {
        this.activeTab = tab;
        mainWindow.showTab(tab);
        mainWindow.updateTabChipSelection(tab);
    }

    /**
     * Curăță resursele tuturor tab-urilor deschise.
     * Apelat la închiderea ferestrei principale (window close button).
     */
    public void closeAllTabs() {
        for (SessionTab tab : new ArrayList<>(tabs)) {
            tab.close();
        }
        tabs.clear();
    }

    private void addTab(SessionTab tab) {
        tabs.add(tab);
        mainWindow.addTabContent(tab);
        mainWindow.addTabChip(tab);
    }
}
