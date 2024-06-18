package org.icepdf.ri.common.views.annotations.summary.mainpanel;

import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.ColorLabelPanel;
import org.icepdf.ri.util.Pair;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Class managing the import and export of the summary window annotations
 */
public interface ImportExportHandler {

    /**
     * Exports the information
     *
     * @param columns The columns to export
     * @param output  The outputstream to save to
     * @throws Exception
     */
    void exportFormat(final List<ColorLabelPanel> columns,
                      final OutputStream output) throws Exception;

    /**
     * Checks that a import is valid
     *
     * @param columns     The columns to check against
     * @param inputStream The input stream to import from
     * @return A map of component to their position if the file is valid, null otherwise
     * @throws Exception
     */
    Map<AnnotationSummaryComponent, Pair<Integer, Integer>> validateImport(final List<ColorLabelPanel> columns,
                                                                           final InputStream inputStream,
                                                                           final boolean partial) throws Exception;

    /**
     * Imports a file to the summary
     *
     * @param compToCell  The map of components to coordinates {@see validateImport}
     * @param inputStream The inputStream of the file
     * @throws Exception
     */
    void importFormat(final Map<AnnotationSummaryComponent, Pair<Integer, Integer>> compToCell,
                      final InputStream inputStream) throws Exception;

    /**
     * @return The file extension used by this importer-exporter
     */
    String getFileExtension();
}
