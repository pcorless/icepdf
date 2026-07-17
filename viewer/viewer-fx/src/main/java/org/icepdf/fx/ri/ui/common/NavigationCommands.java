package org.icepdf.fx.ri.ui.common;

import org.icepdf.fx.ri.viewer.ViewerModel;

/**
 * Helper class for common navigation commands.
 * Provides static methods for page navigation operations.
 */
public class NavigationCommands {

    public static void firstPage(ViewerModel model) {
        if (model.document.get() != null && model.totalPages.get() > 0) {
            model.currentPage.set(1);
            model.statusMessage.set("First page");
        }
    }

    public static void previousPage(ViewerModel model) {
        if (model.document.get() != null && model.currentPage.get() > 1) {
            model.currentPage.set(model.currentPage.get() - 1);
            model.statusMessage.set("Page " + model.currentPage.get());
        }
    }

    public static void nextPage(ViewerModel model) {
        if (model.document.get() != null && model.currentPage.get() < model.totalPages.get()) {
            model.currentPage.set(model.currentPage.get() + 1);
            model.statusMessage.set("Page " + model.currentPage.get());
        }
    }

    public static void lastPage(ViewerModel model) {
        if (model.document.get() != null && model.totalPages.get() > 0) {
            model.currentPage.set(model.totalPages.get());
            model.statusMessage.set("Last page");
        }
    }

    public static void goToPage(ViewerModel model, int pageNumber) {
        if (model.document.get() != null && pageNumber >= 1 && pageNumber <= model.totalPages.get()) {
            model.currentPage.set(pageNumber);
            model.statusMessage.set("Page " + pageNumber);
        }
    }
}

