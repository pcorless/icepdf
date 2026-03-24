package org.icepdf.fx.ri.ui.sidepanel;

import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.fx.ri.viewer.ViewerModel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Panel for viewing and navigating to annotations in the PDF document.
 */
public class AnnotationsPanel extends VBox {

    private static final Logger logger = Logger.getLogger(AnnotationsPanel.class.getName());

    private final ViewerModel model;
    private final TableView<AnnotationItem> annotationsTable;
    private final ComboBox<String> filterComboBox;
    private final Label emptyLabel;
    private final List<AnnotationItem> allAnnotations;

    public AnnotationsPanel(ViewerModel model) {
        this.model = model;
        this.annotationsTable = new TableView<>();
        this.filterComboBox = new ComboBox<>();
        this.emptyLabel = new Label("No annotations");
        this.allAnnotations = new ArrayList<>();

        initializeUI();
        setupBindings();
    }

    private void initializeUI() {
        setSpacing(5);
        setPadding(new Insets(5));

        // Filter ComboBox
        filterComboBox.getItems().addAll(
                "All Types",
                "Text",
                "Highlight",
                "Link",
                "Markup",
                "Widget",
                "Other"
        );
        filterComboBox.setValue("All Types");
        filterComboBox.setMaxWidth(Double.MAX_VALUE);
        filterComboBox.setOnAction(e -> applyFilter());

        // Configure TableView columns
        TableColumn<AnnotationItem, Integer> pageCol = new TableColumn<>("Page");
        pageCol.setCellValueFactory(new PropertyValueFactory<>("pageNumber"));
        pageCol.setPrefWidth(60);

        TableColumn<AnnotationItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(80);

        TableColumn<AnnotationItem, String> contentCol = new TableColumn<>("Content");
        contentCol.setCellValueFactory(new PropertyValueFactory<>("content"));
        contentCol.setPrefWidth(150);

        annotationsTable.getColumns().addAll(pageCol, typeCol, contentCol);
        annotationsTable.setPlaceholder(emptyLabel);
        annotationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Navigate to annotation on double-click
        annotationsTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                AnnotationItem selected = annotationsTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    navigateToAnnotation(selected);
                }
            }
        });

        VBox.setVgrow(annotationsTable, Priority.ALWAYS);

        getChildren().addAll(
                new Label("Filter by type:"),
                filterComboBox,
                annotationsTable
        );
    }

    private void setupBindings() {
        // Listen for document changes
        model.document.addListener((observable, oldDoc, newDoc) -> {
            handleDocumentChange(newDoc);
        });

        // Load annotations if document already exists
        if (model.document.get() != null) {
            handleDocumentChange(model.document.get());
        }
    }

    private void handleDocumentChange(Document newDoc) {
        allAnnotations.clear();
        annotationsTable.getItems().clear();

        if (newDoc != null) {
            try {
                int pageCount = newDoc.getNumberOfPages();

                for (int i = 0; i < pageCount; i++) {
                    Page page = newDoc.getPageTree().getPage(i);
                    if (page != null) {
                        page.init();
                        List<Annotation> pageAnnotations = page.getAnnotations();

                        if (pageAnnotations != null && !pageAnnotations.isEmpty()) {
                            for (Annotation annotation : pageAnnotations) {
                                AnnotationItem item = createAnnotationItem(i + 1, annotation);
                                if (item != null) {
                                    allAnnotations.add(item);
                                }
                            }
                        }
                    }
                }

                // Display all annotations initially
                annotationsTable.getItems().setAll(allAnnotations);

            } catch (Exception e) {
                logger.warning("Error loading annotations: " + e.getMessage());
            }
        }
    }

    private AnnotationItem createAnnotationItem(int pageNumber, Annotation annotation) {
        try {
            String type = getAnnotationType(annotation);
            String content = getAnnotationContent(annotation);

            return new AnnotationItem(pageNumber, type, content, annotation);
        } catch (Exception e) {
            logger.warning("Error creating annotation item: " + e.getMessage());
            return null;
        }
    }

    private String getAnnotationType(Annotation annotation) {
        org.icepdf.core.pobjects.Name subtype = annotation.getSubType();

        if (subtype == null) return "Other";

        if (subtype.equals(Annotation.SUBTYPE_TEXT)) {
            return "Text";
        } else if (subtype.equals(Annotation.SUBTYPE_LINK)) {
            return "Link";
        } else if (subtype.equals(Annotation.SUBTYPE_HIGHLIGHT)) {
            return "Highlight";
        } else if (subtype.equals(TextMarkupAnnotation.SUBTYPE_UNDERLINE)) {
            return "Underline";
        } else if (subtype.equals(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT)) {
            return "Strikeout";
        } else if (subtype.equals(Annotation.SUBTYPE_SQUARE)) {
            return "Square";
        } else if (subtype.equals(Annotation.SUBTYPE_CIRCLE)) {
            return "Circle";
        } else if (subtype.equals(Annotation.SUBTYPE_INK)) {
            return "Ink";
        } else if (subtype.equals(Annotation.SUBTYPE_POPUP)) {
            return "Popup";
        } else if (subtype.equals(Annotation.SUBTYPE_FREE_TEXT)) {
            return "Free Text";
        } else if (subtype.equals(Annotation.SUBTYPE_WIDGET)) {
            return "Widget";
        } else {
            return "Other";
        }
    }

    private String getAnnotationContent(Annotation annotation) {
        try {
            // Try to get annotation contents/title
            String contents = annotation.getContents();
            if (contents != null && !contents.trim().isEmpty()) {
                return truncate(contents, 50);
            }

            // Fallback to annotation type description
            return getAnnotationType(annotation);
        } catch (Exception e) {
            return "(No content)";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private void applyFilter() {
        String filter = filterComboBox.getValue();

        if ("All Types".equals(filter)) {
            annotationsTable.getItems().setAll(allAnnotations);
        } else {
            List<AnnotationItem> filtered = new ArrayList<>();
            for (AnnotationItem item : allAnnotations) {
                if (item.getType().equals(filter)) {
                    filtered.add(item);
                }
            }
            annotationsTable.getItems().setAll(filtered);
        }
    }

    private void navigateToAnnotation(AnnotationItem item) {
        if (item != null) {
            model.currentPage.set(item.getPageNumber());
            // TODO: Highlight or focus the annotation on the page
            logger.info("Navigated to annotation on page " + item.getPageNumber());
        }
    }

    /**
     * Represents an annotation item in the table.
     */
    public static class AnnotationItem {
        private final int pageNumber;
        private final String type;
        private final String content;
        private final Annotation annotation;

        public AnnotationItem(int pageNumber, String type, String content, Annotation annotation) {
            this.pageNumber = pageNumber;
            this.type = type;
            this.content = content;
            this.annotation = annotation;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public Annotation getAnnotation() {
            return annotation;
        }
    }
}

