package org.icepdf.examples.jsf.viewer.renderkit;

import com.sun.faces.util.Util;
import org.icepdf.examples.jsf.viewer.util.FacesUtils;
import org.icepdf.examples.jsf.viewer.view.BeanNames;
import org.icepdf.examples.jsf.viewer.view.DocumentManager;

import javax.faces.application.Resource;
import javax.faces.application.ResourceHandler;
import javax.faces.application.ResourceHandlerWrapper;
import javax.faces.context.FacesContext;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ICEpdf PdfResourceHandler use to serve up the PDF images.
 *
 * @since 5.1.2
 */
public class PdfResourceHandler extends ResourceHandlerWrapper {

    private static final Logger logger =
            Logger.getLogger(PdfResourceHandler.class.toString());
    // resource library name
    private static final String ICEPDF_LIB = "icepdf.core.png";
    private static final byte[] NO_BYTES = new byte[0];
    private static final String CONTENT_TYPE = "image/png";

    private ResourceHandler handler;

    public PdfResourceHandler(ResourceHandler handler) {
        this.handler = handler;
    }

    @Override
    public ResourceHandler getWrapped() {
        return handler;
    }

    @Override
    public Resource createResource(String resourceName, String libraryName) {
        if (ICEPDF_LIB.equals(libraryName)) {
            return new PdfResource(resourceName);
        } else {
            return super.createResource(resourceName, libraryName);
        }
    }

    /**
     * @see javax.faces.application.ResourceHandlerWrapper#libraryExists(java.lang.String)
     */
    @Override
    public boolean libraryExists(final String libraryName) {
        return ICEPDF_LIB.equals(libraryName) || super.libraryExists(libraryName);
    }

    /**
     * @see javax.faces.application.ResourceHandlerWrapper#isResourceRequest(javax.faces.context.FacesContext)
     */
    @Override
    public boolean isResourceRequest(final FacesContext context) {
        return super.isResourceRequest(context);
    }


    private class PdfResource extends Resource {

        private final String mediaId;

        private PdfResource(final String mediaId) {
            setLibraryName(ICEPDF_LIB);
            setResourceName(mediaId);
            setContentType(CONTENT_TYPE);
            this.mediaId = mediaId;
        }

        @Override
        public InputStream getInputStream() throws IOException {

            // return the PDF image.
            BufferedImage bi = null;
            try {
                // get the document manager from the session map.

                DocumentManager documentManager = (DocumentManager)
                        FacesUtils.getManagedBean(BeanNames.DOCUMENT_MANAGER);

                if (documentManager != null) {
                    // get the page image a write it out to the response stream
                    bi = (BufferedImage)
                            documentManager.getCurrentPageImage();
                    if (bi != null) {
                        ByteArrayOutputStream os1 = new ByteArrayOutputStream(512);
                        ImageIO.write(bi, "png", os1);
                        os1.close();
                        bi.flush();
                        return new ByteArrayInputStream(os1.toByteArray());
                    }
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Error writing image stream.", e);
                if (bi != null) {
                    bi.flush();
                }
            }
            return new ByteArrayInputStream(NO_BYTES);
        }

        @Override
        public Map<String, String> getResponseHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public String getRequestPath() {
            final FacesContext context = FacesContext.getCurrentInstance();
            return context
                    .getApplication()
                    .getViewHandler()
                    .getResourceURL(
                            context,
                            ResourceHandler.RESOURCE_IDENTIFIER + "/" + mediaId +
                                    Util.getFacesMapping(context)
                                    + "?ln=" + ICEPDF_LIB);
        }

        public URL getURL() {
            return null;
        }

        public boolean userAgentNeedsUpdate(FacesContext context) {
            return true;
        }

    }
}
