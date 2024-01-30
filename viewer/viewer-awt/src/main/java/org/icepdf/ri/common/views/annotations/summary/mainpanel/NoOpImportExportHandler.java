package org.icepdf.ri.common.views.annotations.summary.mainpanel;

import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.ColorLabelPanel;
import org.icepdf.ri.util.Pair;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * An importer exporter that can't do anything
 */
public class NoOpImportExportHandler implements ImportExportHandler {

    @Override
    public void exportFormat(final List<ColorLabelPanel> columns, final OutputStream output) throws Exception {
        throw new UnsupportedOperationException("No implementation");
    }

    @Override
    public Map<AnnotationSummaryComponent, Pair<Integer, Integer>> validateImport(final List<ColorLabelPanel> columns,
                                                                                  final InputStream inputStream,
                                                                                  final boolean partial) throws Exception {
        return null;
    }

    @Override
    public void importFormat(final Map<AnnotationSummaryComponent, Pair<Integer, Integer>> compToCell,
                             final InputStream inputStream) throws Exception {
        throw new UnsupportedOperationException("No implementation");
    }

    @Override
    public String getFileExtension() {
        return "";
    }
}
