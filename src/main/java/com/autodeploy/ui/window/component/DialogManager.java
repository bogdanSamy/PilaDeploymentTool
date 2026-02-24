package com.autodeploy.ui.window.component;

import com.autodeploy.ui.dialog.CustomAlert;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionează ciclul de viață al dialogurilor modale.
 * <p>
 * Garantează că un singur dialog de fiecare tip e deschis la un moment dat.
 * Dacă dialogul e deja deschis, îl aduce în față (singleton per tip).
 * La închidere, execută callback-ul și curăță referința.
 */
public class DialogManager {

    private static final Logger LOGGER = Logger.getLogger(DialogManager.class.getName());

    private final Stage owner;
    /** Dialoguri active, indexate pe clasă. Maxim o instanță per tip. */
    private final Map<Class<?>, Stage> openDialogs = new HashMap<>();

    public DialogManager(Stage owner) {
        this.owner = owner;
    }

    /**
     * Deschide un dialog sau îl aduce în față dacă e deja deschis.
     * <p>
     * Fluxul:
     * <ol>
     *   <li>Verifică dacă dialogul e deja deschis → toFront()</li>
     *   <li>Creează instanța prin factory (lazy — doar dacă e necesar)</li>
     *   <li>Setează owner + centrare + cleanup la close</li>
     *   <li>Afișează dialogul</li>
     * </ol>
     *
     * @param dialogClass clasa dialogului — folosită ca cheie unică
     * @param factory     creator lazy al instanței
     * @param dialogName  nume pentru logging/error messages
     * @param onClosed    callback opțional executat după închidere (ex: refresh date)
     */
    public <T extends Stage> void openDialog(Class<T> dialogClass,
                                             Supplier<T> factory,
                                             String dialogName,
                                             Runnable onClosed) {
        Stage existing = openDialogs.get(dialogClass);
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            LOGGER.info(dialogName + " dialog already open, bringing to front");
            return;
        }

        try {
            LOGGER.info("Opening " + dialogName + " Dialog");

            T dialog = factory.get();
            dialog.initOwner(owner);
            dialog.centerOnScreen();

            dialog.setOnHidden(event -> {
                openDialogs.remove(dialogClass);
                if (onClosed != null) {
                    onClosed.run();
                }
                LOGGER.info(dialogName + " Dialog closed");
            });

            openDialogs.put(dialogClass, dialog);
            dialog.show();

        } catch (Exception ex) {
            openDialogs.remove(dialogClass);
            LOGGER.log(Level.SEVERE, "Error opening " + dialogName + " dialog", ex);
            CustomAlert.showError("Dialog Error",
                    "Failed to open " + dialogName + " dialog:\n" + ex.getMessage());
        }
    }
}