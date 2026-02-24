package com.autodeploy.ui.window.component;

import com.autodeploy.service.scanner.FileWatcher;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.autodeploy.core.constants.Constants.*;

/**
 * Componentă UI reutilizabilă: listă de fișiere cu checkbox-uri.
 * Folosită atât pentru JAR-uri cât și pentru JSP-uri.
 * <p>
 * Funcționalitate:
 * <ul>
 *   <li>Încărcare inițială din scan ({@link #loadFiles})</li>
 *   <li>Update live de la {@link FileWatcher} ({@link #handleFileChange})</li>
 *   <li>Filtrare text ({@link #filter})</li>
 *   <li>Contor "X / Y selected" actualizat automat</li>
 * </ul>
 * <p>
 * Fișierele noi sau modificate sunt adăugate pre-selectate și cu stil highlighted
 * (bold, accent color) — semnalează vizual ce s-a schimbat de la ultimul upload.
 * Fișierele noi sunt inserate la ÎNCEPUTUL listei ({@code addFirst}).
 */
public class FileListPanel {

    private final VBox container;
    private final Label countLabel;
    private final String emptyMessage;
    private final Consumer<String> logger;

    /** LinkedHashMap pentru a păstra ordinea de inserție. */
    private final Map<String, CheckBox> checkBoxMap = new LinkedHashMap<>();

    public FileListPanel(VBox container, Label countLabel,
                         String emptyMessage, Consumer<String> logger) {
        this.container = container;
        this.countLabel = countLabel;
        this.emptyMessage = emptyMessage;
        this.logger = logger;
    }

    /** Încărcare inițială — toate fișierele sunt neselectate. */
    public void loadFiles(List<String> fileNames) {
        Platform.runLater(() -> {
            container.getChildren().clear();
            checkBoxMap.clear();

            if (fileNames.isEmpty()) {
                Label noFiles = new Label(emptyMessage);
                noFiles.setStyle(STYLE_MUTED_TEXT);
                container.getChildren().add(noFiles);
            } else {
                fileNames.forEach(name -> addFileInternal(name, false));
            }

            updateCount();
        });
    }

    /**
     * Handler pentru evenimentele de la FileWatcher.
     * Poate fi pasat direct ca method reference: {@code panel::handleFileChange}.
     * <p>
     * MODIFIED = remove + add cu checked=true (re-inserare la începutul listei,
     * marcată ca "changed" pentru a atrage atenția).
     */
    public void handleFileChange(FileWatcher.FileChangeEvent event) {
        String path = event.getRelativePath();

        switch (event.getType()) {
            case ADDED -> {
                log("➕ New file detected: " + path);
                addFile(path, true);
            }
            case MODIFIED -> {
                log("✏️ File modified: " + path);
                removeFile(path);
                addFile(path, true);
            }
            case DELETED -> {
                log("➖ File deleted: " + path);
                removeFile(path);
            }
        }
    }

    public void addFile(String fileName, boolean checked) {
        Platform.runLater(() -> {
            CheckBox existing = checkBoxMap.get(fileName);
            if (existing != null) {
                container.getChildren().remove(existing);
            }

            addFileInternal(fileName, checked);
            updateCount();
        });
    }

    public void removeFile(String fileName) {
        Platform.runLater(() -> {
            CheckBox checkBox = checkBoxMap.remove(fileName);
            if (checkBox != null) {
                container.getChildren().remove(checkBox);
                updateCount();
            }
        });
    }

    /**
     * Filtrare text — afișează doar checkbox-urile care conțin textul căutat.
     * Nu elimină fișierele din map, doar le ascunde/afișează în container.
     */
    public void filter(String searchText) {
        Platform.runLater(() -> {
            container.getChildren().clear();

            if (searchText == null || searchText.trim().isEmpty()) {
                container.getChildren().addAll(checkBoxMap.values());
            } else {
                String lowerSearch = searchText.toLowerCase();
                List<CheckBox> filtered = checkBoxMap.entrySet().stream()
                        .filter(entry -> entry.getKey().toLowerCase().contains(lowerSearch))
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList());
                container.getChildren().addAll(filtered);
            }

            updateCount();
        });
    }

    public Map<String, CheckBox> getCheckBoxMap() {
        return checkBoxMap;
    }

    /**
     * Creează checkbox-ul și îl inserează la ÎNCEPUTUL containerului.
     * Fișierele checked primesc stil highlighted (bold + accent color).
     */
    private void addFileInternal(String fileName, boolean checked) {
        CheckBox checkBox = new CheckBox(fileName);
        checkBox.setSelected(checked);
        checkBox.setStyle(checked ? STYLE_CHECKBOX_HIGHLIGHTED : STYLE_CHECKBOX_DEFAULT);
        checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> updateCount());

        checkBoxMap.put(fileName, checkBox);
        container.getChildren().addFirst(checkBox);
    }

    private void updateCount() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateCount);
            return;
        }

        long selected = checkBoxMap.values().stream().filter(CheckBox::isSelected).count();
        long total = checkBoxMap.size();
        countLabel.setText(selected + " / " + total + " selected");
    }

    private void log(String message) {
        if (logger != null) logger.accept(message);
    }
}