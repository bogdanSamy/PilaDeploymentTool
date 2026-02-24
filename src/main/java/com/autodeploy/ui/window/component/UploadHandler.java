package com.autodeploy.ui.window.component;

import com.autodeploy.service.deploy.FileUploadService;
import com.autodeploy.ui.dialog.CustomAlert;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;

import java.util.Map;
import java.util.function.Consumer;

import static com.autodeploy.core.constants.Constants.MSG_NO_FILES_SELECTED;
import static com.autodeploy.core.constants.Constants.MSG_NOT_CONNECTED;

/**
 * Orchestrează upload-ul fișierelor JAR și JSP.
 * <p>
 * Responsabilități:
 * <ul>
 *   <li>Validare pre-upload: fișiere selectate? conexiune activă?</li>
 *   <li>Disable butoane de upload în timpul operației (previne double-click)</li>
 *   <li>Upload secvențial pe thread daemon (JAR-uri, apoi JSP-uri)</li>
 * </ul>
 * <p>
 * {@link #uploadAll()} uploadează JAR-urile primele. Dacă conexiunea se pierde
 * în timpul upload-ului JAR, skip-ează JSP-urile (nu are sens să încerce).
 */
public class UploadHandler {

    private final FileListPanel jarPanel;
    private final FileListPanel jspPanel;
    private final FileUploadService fileUploadService;
    private final ConnectionHandler connectionHandler;
    private final DeploymentActionBar actionBar;
    private final Consumer<String> logger;

    public UploadHandler(FileListPanel jarPanel, FileListPanel jspPanel,
                         FileUploadService fileUploadService,
                         ConnectionHandler connectionHandler,
                         DeploymentActionBar actionBar,
                         Consumer<String> logger) {
        this.jarPanel = jarPanel;
        this.jspPanel = jspPanel;
        this.fileUploadService = fileUploadService;
        this.connectionHandler = connectionHandler;
        this.actionBar = actionBar;
        this.logger = logger;
    }

    public void uploadJars() {
        var selectedJars = jarPanel.getCheckBoxMap();
        if (!validateSelection(selectedJars)) return;
        if (!checkConnection()) return;

        actionBar.setUploadDisabled(true);
        AsyncHelper.runDaemon(() -> {
            fileUploadService.uploadJars(selectedJars);
            Platform.runLater(() -> actionBar.setUploadDisabled(false));
        }, "JAR-Upload");
    }

    public void uploadJsps() {
        var selectedJsps = jspPanel.getCheckBoxMap();
        if (!validateSelection(selectedJsps)) return;
        if (!checkConnection()) return;

        actionBar.setUploadDisabled(true);
        AsyncHelper.runDaemon(() -> {
            fileUploadService.uploadJsps(selectedJsps);
            Platform.runLater(() -> actionBar.setUploadDisabled(false));
        }, "JSP-Upload");
    }

    /**
     * Uploadează toate fișierele selectate: JAR-uri primele, apoi JSP-uri.
     * Dacă conexiunea se pierde în timpul JAR-urilor, skip-ează JSP-urile.
     */
    public void uploadAll() {
        var jarMap = jarPanel.getCheckBoxMap();
        var jspMap = jspPanel.getCheckBoxMap();

        long jarCount = countSelected(jarMap);
        long jspCount = countSelected(jspMap);

        if (jarCount == 0 && jspCount == 0) {
            logger.accept("⚠ No files selected");
            CustomAlert.showWarning("No Files Selected", MSG_NO_FILES_SELECTED);
            return;
        }
        if (!checkConnection()) return;

        logger.accept("📤 Starting upload of all selected files...");
        actionBar.setUploadDisabled(true);

        AsyncHelper.runDaemon(() -> {
            if (jarCount > 0) {
                Platform.runLater(() -> logger.accept("### Uploading JARs ###"));
                var jarResult = fileUploadService.uploadJars(jarMap);
                if (jarResult.isConnectionLost()) {
                    Platform.runLater(() -> {
                        logger.accept("⚠ Skipping JSP upload due to connection loss");
                        actionBar.setUploadDisabled(false);
                    });
                    return;
                }
            }

            if (jspCount > 0) {
                Platform.runLater(() -> logger.accept("### Uploading JSPs ###"));
                fileUploadService.uploadJsps(jspMap);
            }

            Platform.runLater(() -> {
                logger.accept("####################################");
                logger.accept("✓ All uploads completed");
                actionBar.setUploadDisabled(false);
            });
        }, "Upload-All");
    }

    private boolean validateSelection(Map<String, CheckBox> selectionMap) {
        if (selectionMap.values().stream().noneMatch(CheckBox::isSelected)) {
            CustomAlert.showWarning("No Files Selected", MSG_NO_FILES_SELECTED);
            return false;
        }
        return true;
    }

    private boolean checkConnection() {
        if (!connectionHandler.isConnected()) {
            CustomAlert.showError("Connection Error", MSG_NOT_CONNECTED);
            return false;
        }
        return true;
    }

    private long countSelected(Map<String, CheckBox> map) {
        return map.values().stream().filter(CheckBox::isSelected).count();
    }
}