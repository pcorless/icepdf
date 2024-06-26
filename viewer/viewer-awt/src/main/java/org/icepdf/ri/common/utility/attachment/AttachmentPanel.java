package org.icepdf.ri.common.utility.attachment;

import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.ViewModel;
import org.icepdf.ri.images.IconPack;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.viewer.WindowManager;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.*;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.ri.common.utility.attachment.FileTableModel.*;

/**
 * AttachmentPanel displays a PDF attachments as defined by the Catalogs names tree's EmbeddedFiles entry.
 * The view is pretty straight forward showing properties on all attached files but only allows the opening
 * of .pdf files via a double click of a row.  However, it is possible to save any file by selecting a table
 * row and right-clicking to expose the context menu for 'Save as...'
 *
 * @since 6.2
 */
@SuppressWarnings("serial")
public class AttachmentPanel extends JPanel implements MouseListener, ActionListener, MutableDocument {

    private static final Logger logger =
            Logger.getLogger(AttachmentPanel.class.toString());

    private static final String PDF_EXTENSION = ".pdf";

    private final org.icepdf.ri.common.views.Controller controller;

    private JTable fileTable;
    private FileTableModel fileTableModel;
    private JPopupMenu contextMenu;
    private JMenuItem saveAsMenuItem;
    private HashMap<String, FileSpecification> files;

    // message bundle for internationalization
    private final ResourceBundle messageBundle;

    public AttachmentPanel(SwingController controller) {
        this.controller = controller;
        this.setFocusable(true);
        this.messageBundle = this.controller.getMessageBundle();
    }

    private void buildUI() {
        fileTableModel = new FileTableModel(messageBundle, files);
        fileTable = new JTable(fileTableModel) {
            // Implement table cell tool tips.
            public String getToolTipText(MouseEvent e) {
                String tip;
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);
                int realColumnIndex = convertColumnIndexToModel(colIndex);
                if (realColumnIndex == NAME_COLUMN) {
                    TableModel model = getModel();
                    tip = (String) model.getValueAt(rowIndex, NAME_COLUMN);
                } else if (realColumnIndex == DESCRIPTION_COLUMN) {
                    TableModel model = getModel();
                    tip = (String) model.getValueAt(rowIndex, DESCRIPTION_COLUMN);
                } else if (realColumnIndex == MODIFIED_COLUMN) {
                    TableModel model = getModel();
                    tip = model.getValueAt(rowIndex, MODIFIED_COLUMN).toString();
                } else if (realColumnIndex == SIZE_COLUMN) {
                    TableModel model = getModel();
                    tip = model.getValueAt(rowIndex, SIZE_COLUMN).toString();
                } else if (realColumnIndex == COMPRESSION_COLUMN) {
                    TableModel model = getModel();
                    tip = model.getValueAt(rowIndex, COMPRESSION_COLUMN).toString();
                } else {
                    tip = super.getToolTipText(e);
                }
                return tip;
            }
        };
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // try and show all the file name label.
        fileTable.getColumnModel().getColumn(NAME_COLUMN).setPreferredWidth(225);
        fileTable.getColumnModel().getColumn(DESCRIPTION_COLUMN).setPreferredWidth(50);
        fileTable.getColumnModel().getColumn(MODIFIED_COLUMN).setPreferredWidth(75);
        fileTable.getColumnModel().getColumn(SIZE_COLUMN).setPreferredWidth(75);
        fileTable.getColumnModel().getColumn(COMPRESSION_COLUMN).setPreferredWidth(75);
        // add double click support for row, so we can load the file if it's pdf.
        fileTable.addMouseListener(this);
        // right click context menu for save as.
        contextMenu = new JPopupMenu();
        saveAsMenuItem = new JMenuItem(messageBundle.getString(
                "viewer.utilityPane.attachments.menu.saveAs.label"));
        Images.applyIcon(saveAsMenuItem, "save", IconPack.Variant.NORMAL, Images.IconSize.SMALL);
        saveAsMenuItem.addActionListener(this);
        contextMenu.add(saveAsMenuItem);

        // remove any previous UI components.
        this.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(fileTable,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        this.add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Sets the given document as the current and builds the embedded file table is possible.
     */
    @Override
    public void refreshDocumentInstance() {
        disposeDocument();
        Document document = controller.getDocument();
        if (document != null) {
            Catalog catalog = document.getCatalog();
            // grab each file pair and build out the FileSpecification objects.
            if (catalog.getEmbeddedFilesNameTree() != null) {
                NameTree embeddedFilesNameTree = catalog.getEmbeddedFilesNameTree();
                java.util.List filePairs = embeddedFilesNameTree.getNamesAndValues();
                if (filePairs != null) {
                    Library library = catalog.getLibrary();
                    // check to see if at least one file is a PDF.
                    int max = filePairs.size();
                    files = new HashMap<>(max / 2);
                    for (int i = 0; i < max; i += 2) {
                        // get the name and document for
                        // file name and file specification pairs.
                        Object rawFileName = library.getObject(filePairs.get(i));
                        Object rawFileProperties = library.getObject(filePairs.get(i + 1));
                        if (rawFileName instanceof LiteralStringObject &&
                                rawFileProperties instanceof HashMap) {
                            String fileName = Utils.convertStringObject(library, (LiteralStringObject) rawFileName);
                            files.put(fileName, new FileSpecification(library, (DictionaryEntries) rawFileProperties));
                        }
                    }
                    buildUI();
                }
            }
        }
    }

    @Override
    public void disposeDocument() {
        this.removeAll();
        files = null;
        fileTableModel = null;
    }

    public void actionPerformed(ActionEvent e) {
        // show save as dialog and try to save the file stream.
        if (e.getSource().equals(saveAsMenuItem)) {
            int selectedRow = fileTable.getSelectedRow();
            Object value = fileTableModel.getValueAt(selectedRow, DATA_COLUMN);
            if (value instanceof FileSpecification) {
                FileSpecification fileSpecification = (FileSpecification) value;
                final EmbeddedFileStream embeddedFileStream = fileSpecification.getEmbeddedFileStream();
                if (embeddedFileStream != null) {
                    final String fileName = (String) fileTableModel.getValueAt(selectedRow, NAME_COLUMN);
                    // already on awt thread but still nice to play by the rules.
                    Runnable doSwingWork = () -> saveFile(fileName, embeddedFileStream);
                    SwingUtilities.invokeLater(doSwingWork);
                }
            }
        }
    }

    public void mouseClicked(MouseEvent mouseEvent) {
        // try and do double click file opening of PDF documents.
        if (mouseEvent.getClickCount() == 2 && mouseEvent.getButton() == MouseEvent.BUTTON1) {
            int selectedRow = fileTable.getSelectedRow();
            Object value = fileTableModel.getValueAt(selectedRow, DATA_COLUMN);
            if (value instanceof FileSpecification) {
                FileSpecification fileSpecification = (FileSpecification) value;
                EmbeddedFileStream embeddedFileStream = fileSpecification.getEmbeddedFileStream();
                String fileName = (String) fileTableModel.getValueAt(selectedRow, NAME_COLUMN);
                // load the file stream if it's PDF.
                if (embeddedFileStream != null && fileName.toLowerCase().endsWith(PDF_EXTENSION)) {
                    try {
                        InputStream fileInputStream = embeddedFileStream.getDecodedStreamData();
                        Document embeddedDocument = new Document();
                        embeddedDocument.setInputStream(fileInputStream, fileName);
                        WindowManager.getInstance().newWindow(embeddedDocument, fileName);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Error opening PDF file stream " + fileName, e);
                    } catch (PDFSecurityException e) {
                        logger.log(Level.WARNING, "Error opening PDF security exception " + fileName, e);
                    }
                }
            }
        }
        if (mouseEvent.getButton() == MouseEvent.BUTTON3 || mouseEvent.getButton() == MouseEvent.BUTTON2) {
            int row = fileTable.rowAtPoint(mouseEvent.getPoint());
            // if pointer is over a selected row, show popup
            if (fileTable.isRowSelected(row)) {
                contextMenu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
            }
        }
    }

    public void mousePressed(MouseEvent e) {

    }

    public void mouseReleased(MouseEvent e) {

    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    /**
     * Utility method to save the embedded file stream as a separate file.  Any file can be saved not just
     * limited to PDF.
     *
     * @param fileName           filename associated with he embedded file stream.
     * @param embeddedFileStream embedded file stream data to save. .
     */
    private void saveFile(String fileName, EmbeddedFileStream embeddedFileStream) {

        // Create and display a file saving dialog
        final FileDialog fileDialog = new FileDialog(controller.getViewerFrame());
        fileDialog.setTitle(messageBundle.getString("viewer.dialog.saveAs.title"));
        fileDialog.setMultipleMode(false);
        fileDialog.setMode(FileDialog.SAVE);
        // set the directory to our currently set default.
        if (ViewModel.getDefaultFile() != null) {
            fileDialog.setDirectory(ViewModel.getDefaultFile().getAbsolutePath());
        }
        // set the file name.
        fileDialog.setFile(fileName);
        // show the dialog
        fileDialog.setVisible(true);
        final String filePath = fileDialog.getFile();
        final String dirPath = fileDialog.getDirectory();
        if (filePath != null && dirPath != null) {
            File file = new File(dirPath + filePath);
            if (file.exists()) {
                boolean overWrite = org.icepdf.ri.util.Resources.showConfirmDialog(
                        controller.getViewerFrame(),
                        messageBundle,
                        "viewer.utilityPane.attachments.saveAs.replace.title",
                        "viewer.utilityPane.attachments.saveAs.replace.msg",
                        fileName);
                if (!overWrite) {
                    // ask again.
                    saveFile(fileName, embeddedFileStream);
                }
            }
            // save file stream
            try {
                InputStream inputStream = embeddedFileStream.getDecodedStreamData();
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                BufferedOutputStream buf = new BufferedOutputStream(fileOutputStream, 8192);
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    buf.write(buffer, 0, length);
                }
                buf.flush();
                fileOutputStream.flush();
                buf.close();
                fileOutputStream.close();
                inputStream.close();
            } catch (MalformedURLException e) {
                logger.log(Level.FINE, "Malformed URL Exception ", e);
            } catch (IOException e) {
                logger.log(Level.FINE, "IO Exception ", e);
            }
            // save the default directory
            ViewModel.setDefaultFile(file);
        }
    }
}
