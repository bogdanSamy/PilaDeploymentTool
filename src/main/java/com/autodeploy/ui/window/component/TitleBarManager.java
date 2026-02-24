package com.autodeploy.ui.window.component;

import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import xss.it.nfx.WindowState;

import java.util.Objects;

import static com.autodeploy.core.constants.Constants.MAX_SHAPE;
import static com.autodeploy.core.constants.Constants.REST_SHAPE;

/**
 * Configurează title bar-ul custom pentru ferestrele NFX (undecorated).
 * <p>
 * Sincronizează:
 * <ul>
 *   <li>Iconița din title bar cu {@code stage.getIcons()}</li>
 *   <li>Label-ul de titlu cu {@code stage.titleProperty()}</li>
 *   <li>SVG-ul butonului maximize/restore cu {@code windowStateProperty()}</li>
 * </ul>
 * <p>
 * Butoanele (close, maximize, minimize) sunt legate la acțiunile ferestrei
 * prin {@link WindowControlsBinder} — un delegate necesar deoarece metodele
 * NFX ({@code bindCloseButton}, etc.) sunt protejate și accesibile doar
 * din subclasa ferestrei.
 */
public class TitleBarManager {

    /**
     * Delegate pentru binding-ul butoanelor la acțiunile NFX protejate.
     * Implementat de fereastra concretă (ex: DeploymentWindow, SelectionWindow).
     */
    @FunctionalInterface
    public interface WindowControlsBinder {
        void bindControls(Button closeBtn, Button maxBtn, Button minBtn);
    }

    private final Stage stage;
    private final Button closeBtn;
    private final Button maxBtn;
    private final Button minBtn;
    private final SVGPath maxShape;
    private final ImageView iconView;
    private final Label titleLabel;
    private final WindowControlsBinder controlsBinder;

    public TitleBarManager(Stage stage, Button closeBtn, Button maxBtn, Button minBtn,
                           SVGPath maxShape, ImageView iconView, Label titleLabel,
                           WindowControlsBinder controlsBinder) {
        this.stage = stage;
        this.closeBtn = closeBtn;
        this.maxBtn = maxBtn;
        this.minBtn = minBtn;
        this.maxShape = maxShape;
        this.iconView = iconView;
        this.titleLabel = titleLabel;
        this.controlsBinder = controlsBinder;
    }

    public void setup() {
        stage.getIcons().addListener((ListChangeListener<? super Image>) observable -> {
            if (!stage.getIcons().isEmpty()) {
                iconView.setImage(stage.getIcons().getFirst());
            }
        });

        stage.titleProperty().addListener((obs, oldVal, newVal) -> titleLabel.setText(newVal));

        controlsBinder.bindControls(closeBtn, maxBtn, minBtn);

        if (stage instanceof xss.it.nfx.NfxStage nfxStage) {
            updateMaxShape(nfxStage.getWindowState());
            nfxStage.windowStateProperty().addListener(
                    (obs, oldState, newState) -> updateMaxShape(newState));
        }
    }

    /** Alternează SVG-ul între maximize (□) și restore (⧉) pe baza stării ferestrei. */
    private void updateMaxShape(WindowState state) {
        maxShape.setContent(
                Objects.equals(state, WindowState.MAXIMIZED) ? REST_SHAPE : MAX_SHAPE);
    }
}