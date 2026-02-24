package com.autodeploy.ui.window.component;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.application.Platform;

import java.util.List;

/**
 * Gestionează starea butoanelor din bara de acțiuni a ferestrei de deployment.
 * <p>
 * Centralizează enable/disable pentru a evita inconsistențe
 * (ex: upload activ dar butonul de restart enabled, sau butoane active
 * în timp ce conexiunea e pierdută).
 * <p>
 * Toate operațiile sunt thread-safe (Platform.runLater).
 */
public class DeploymentActionBar {

    private final MFXButton restartServerBtn;
    private final MFXButton downloadLogsBtn;
    private final MFXButton buildProjectBtn;
    private final MFXButton openBrowserBtn;
    private final MFXButton uploadJarsBtn;
    private final MFXButton uploadJspsBtn;
    private final MFXButton uploadAllBtn;

    private final List<MFXButton> allActionButtons;
    private final List<MFXButton> uploadButtons;

    public DeploymentActionBar(MFXButton restartServerBtn, MFXButton downloadLogsBtn,
                               MFXButton buildProjectBtn, MFXButton openBrowserBtn,
                               MFXButton uploadJarsBtn, MFXButton uploadJspsBtn,
                               MFXButton uploadAllBtn) {
        this.restartServerBtn = restartServerBtn;
        this.downloadLogsBtn = downloadLogsBtn;
        this.buildProjectBtn = buildProjectBtn;
        this.openBrowserBtn = openBrowserBtn;
        this.uploadJarsBtn = uploadJarsBtn;
        this.uploadJspsBtn = uploadJspsBtn;
        this.uploadAllBtn = uploadAllBtn;

        this.uploadButtons = List.of(uploadJarsBtn, uploadJspsBtn, uploadAllBtn);
        this.allActionButtons = List.of(
                restartServerBtn, downloadLogsBtn, buildProjectBtn,
                openBrowserBtn, uploadJarsBtn, uploadJspsBtn, uploadAllBtn
        );
    }

    /** Dezactivează tot — folosit la pierderea conexiunii. */
    public void setAllDisabled(boolean disabled) {
        Platform.runLater(() -> allActionButtons.forEach(btn -> btn.setDisable(disabled)));
    }

    /** Dezactivează doar upload-urile — folosit în timpul unui upload activ. */
    public void setUploadDisabled(boolean disabled) {
        Platform.runLater(() -> uploadButtons.forEach(btn -> btn.setDisable(disabled)));
    }

    public MFXButton getRestartServerBtn() { return restartServerBtn; }
    public MFXButton getDownloadLogsBtn() { return downloadLogsBtn; }
    public MFXButton getBuildProjectBtn() { return buildProjectBtn; }
}