package com.autodeploy.ui.dialog.helper;

import javafx.scene.control.Button;
import xss.it.nfx.AbstractNfxUndecoratedWindow;
import xss.it.nfx.HitSpot;

import java.util.List;

/**
 * Helper pentru configurarea decorațiunilor ferestrei (HitSpots din NFX).
 * <p>
 * NFX (NfxUndecoratedWindow) necesită HitSpots pentru a defini zonele interactive
 * ale title bar-ului (close, minimize, maximize). Această clasă centralizează
 * crearea HitSpot-urilor cu hover effect — evită duplicarea din fiecare dialog.
 */
public final class WindowDecorationHelper {

    private static final String CLOSE_HOVER_STYLE = "hit-close-btn";

    private WindowDecorationHelper() {}

    /**
     * Creează un HitSpot de close cu hover effect (adaugă/elimină CSS class).
     * <p>
     * HitSpot-ul leagă butonul fizic de acțiunea de close a ferestrei NFX,
     * și adaugă un listener pe {@code hoveredProperty} pentru a aplica
     * stilul vizual la hover (roșu pe butonul de close, tipic).
     */
    public static List<HitSpot> createCloseHitSpot(AbstractNfxUndecoratedWindow window, Button closeBtn) {
        HitSpot spot = HitSpot.builder()
                .window(window)
                .control(closeBtn)
                .close(true)
                .build();

        spot.hoveredProperty().addListener((obs, oldVal, hovered) -> {
            if (hovered) {
                if (!spot.getControl().getStyleClass().contains(CLOSE_HOVER_STYLE)) {
                    spot.getControl().getStyleClass().add(CLOSE_HOVER_STYLE);
                }
            } else {
                spot.getControl().getStyleClass().remove(CLOSE_HOVER_STYLE);
            }
        });

        return List.of(spot);
    }
}