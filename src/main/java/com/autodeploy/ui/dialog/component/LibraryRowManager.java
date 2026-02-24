package com.autodeploy.ui.dialog.component;

import com.autodeploy.ui.dialog.helper.FileBrowserHelper;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestionează rândurile dinamice de librării Ant din formularul de proiect.
 * <p>
 * Fiecare rând conține: TextField (cale JAR) + Browse + Remove.
 * Rândurile se adaugă/elimină dinamic, iar fiecare modificare declanșează
 * regenerarea comenzii Ant (prin {@code onChangeCallback}).
 * <p>
 * Vizibilitatea se adaptează:
 * <ul>
 *   <li>Fără librării: se afișează butonul inline "Add Library"</li>
 *   <li>Cu librării: se afișează lista + footer cu buton "Add Another"</li>
 * </ul>
 */
public class LibraryRowManager {

    private final VBox librariesContainer;
    private final HBox librariesFooter;
    private final Button addLibraryInlineBtn;
    private final ScrollPane mainScrollPane;
    private final Window ownerWindow;
    private final Runnable onChangeCallback;

    private final List<LibraryRow> libraryRows = new ArrayList<>();

    private static class LibraryRow {
        final HBox container;
        final TextField pathField;

        LibraryRow(HBox container, TextField pathField) {
            this.container = container;
            this.pathField = pathField;
        }

        String getPath() {
            return pathField.getText().trim();
        }

        boolean isValid() {
            return !getPath().isEmpty();
        }
    }

    public LibraryRowManager(VBox librariesContainer, HBox librariesFooter,
                             Button addLibraryInlineBtn, ScrollPane mainScrollPane,
                             Window ownerWindow, Runnable onChangeCallback) {
        this.librariesContainer = librariesContainer;
        this.librariesFooter = librariesFooter;
        this.addLibraryInlineBtn = addLibraryInlineBtn;
        this.mainScrollPane = mainScrollPane;
        this.ownerWindow = ownerWindow;
        this.onChangeCallback = onChangeCallback;
    }

    /**
     * Adaugă un rând nou de librărie. Salvează și restaurează poziția de scroll
     * pentru a evita saltul vizual la adăugare.
     */
    public void addRow(String path) {
        double scrollPosition = mainScrollPane.getVvalue();

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        TextField pathField = new TextField(path);
        pathField.setPromptText("C:\\path\\to\\library.jar");
        pathField.getStyleClass().addAll("field-input", "library-input");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse");
        browseBtn.getStyleClass().add("lib-browse-btn");
        browseBtn.setMinWidth(70);

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("remove-lib-btn");

        row.getChildren().addAll(pathField, browseBtn, removeBtn);

        LibraryRow libRow = new LibraryRow(row, pathField);
        libraryRows.add(libRow);
        librariesContainer.getChildren().add(row);

        browseBtn.setOnAction(e -> FileBrowserHelper.browseForJarFile(ownerWindow, pathField));
        removeBtn.setOnAction(e -> removeRow(libRow));
        pathField.textProperty().addListener((obs, old, newVal) -> notifyChange());

        notifyChange();
        updateVisibility();

        Platform.runLater(() -> {
            mainScrollPane.setVvalue(scrollPosition);
            pathField.requestFocus();
        });
    }

    public void clearAll() {
        libraryRows.clear();
        librariesContainer.getChildren().clear();
        updateVisibility();
    }

    /** Returnează doar căile non-empty (ignoră rândurile goale). */
    public List<String> getLibraryPaths() {
        List<String> paths = new ArrayList<>();
        for (LibraryRow row : libraryRows) {
            if (row.isValid()) {
                paths.add(row.getPath());
            }
        }
        return paths;
    }

    public void loadLibraries(List<String> libraries) {
        clearAll();
        if (libraries != null) {
            for (String lib : libraries) {
                if (lib != null && !lib.trim().isEmpty()) {
                    addRow(lib);
                }
            }
        }
        updateVisibility();
    }

    /**
     * Alternează între modul "empty" (buton inline) și modul "list" (container + footer).
     */
    public void updateVisibility() {
        boolean hasLibraries = !libraryRows.isEmpty();

        addLibraryInlineBtn.setVisible(!hasLibraries);
        addLibraryInlineBtn.setManaged(!hasLibraries);

        librariesContainer.setVisible(hasLibraries);
        librariesContainer.setManaged(hasLibraries);
        librariesFooter.setVisible(hasLibraries);
        librariesFooter.setManaged(hasLibraries);
    }

    private void removeRow(LibraryRow row) {
        double scrollPosition = mainScrollPane.getVvalue();
        libraryRows.remove(row);
        librariesContainer.getChildren().remove(row.container);
        notifyChange();
        updateVisibility();
        Platform.runLater(() -> mainScrollPane.setVvalue(scrollPosition));
    }

    /** Notifică parent-ul (ProjectFormBinder) pentru regenerare comandă Ant. */
    private void notifyChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }
}