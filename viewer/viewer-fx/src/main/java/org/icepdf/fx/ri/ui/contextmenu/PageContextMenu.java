package org.icepdf.fx.ri.ui.contextmenu;

import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Context menu for page-specific operations.
 */
public class PageContextMenu extends ContextMenu {
    private final ViewerModel model;
    private final Window window;
    private final int pageNumber;

    public PageContextMenu(ViewerModel model, Window window, int pageNumber) {
        this.model = model;
        this.window = window;
        this.pageNumber = pageNumber;
        buildMenu();
    }

    private void buildMenu() {
        MenuItem goToPage = new MenuItem("Go to Page " + pageNumber);
        goToPage.setOnAction(e -> model.currentPage.set(pageNumber));
        MenuItem extractPage = new MenuItem("Extract Page...");
        extractPage.setOnAction(e -> model.statusMessage.set("Extract page not yet implemented"));
        MenuItem deletePage = new MenuItem("Delete Page...");
        deletePage.setOnAction(e -> model.statusMessage.set("Delete page not yet implemented"));
        deletePage.setDisable(model.totalPages.get() <= 1);
        MenuItem rotateLeft = new MenuItem("Rotate Page Left");
        rotateLeft.setOnAction(e -> model.statusMessage.set("Rotate page not yet implemented"));
        MenuItem rotateRight = new MenuItem("Rotate Page Right");
        rotateRight.setOnAction(e -> model.statusMessage.set("Rotate page not yet implemented"));
        MenuItem properties = new MenuItem("Page Properties...");
        properties.setOnAction(e -> showProperties());
        getItems().addAll(goToPage, new SeparatorMenuItem(), extractPage, deletePage,
                new SeparatorMenuItem(), rotateLeft, rotateRight, new SeparatorMenuItem(), properties);
    }

    private void showProperties() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(window);
        alert.setTitle("Page Properties");
        alert.setHeaderText("Page " + pageNumber);
        Document doc = model.document.get();
        if (doc != null) {
            try {
                PDimension size = doc.getPageDimension(pageNumber - 1, 0f, 1.0f);
                alert.setContentText(String.format("Width: %.2f pt\nHeight: %.2f pt",
                        size.getWidth(), size.getHeight()));
            } catch (Exception e) {
                alert.setContentText("Unable to retrieve properties");
            }
        }
        alert.showAndWait();
    }
}
