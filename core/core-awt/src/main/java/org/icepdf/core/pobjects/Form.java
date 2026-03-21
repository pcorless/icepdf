/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils;
import org.icepdf.core.pobjects.graphics.ExtGState;
import org.icepdf.core.pobjects.graphics.GraphicsState;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.parser.content.ContentParser;
import org.icepdf.core.util.updater.callbacks.ContentStreamCallback;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.Resources.FONT_KEY;
import static org.icepdf.core.pobjects.Resources.XOBJECT_KEY;
import static org.icepdf.core.pobjects.annotations.utils.ContentWriterUtils.EMBEDDED_FONT_NAME;

/**
 * Form XObject class. Not currently part of the public api.
 * <br>
 * Forms are grouped into the 'Resource' category and can be shared.  As a result we need to make sure
 * that the init method are synchronized as they can be accessed by different page loading threads.
 *
 * @since 1.0
 */
public class Form extends Stream {

    private static final Logger logger = Logger.getLogger(Form.class.toString());

    public static final Name TYPE_VALUE = new Name("XObject");
    public static final Name SUB_TYPE_VALUE = new Name("Form");
    public static final Name GROUP_KEY = new Name("Group");
    public static final Name I_KEY = new Name("I");
    public static final Name K_KEY = new Name("K");
    public static final Name MATRIX_KEY = new Name("Matrix");
    public static final Name BBOX_KEY = new Name("BBox");
    public static final Name RESOURCES_KEY = new Name("Resources");

    private AffineTransform matrix = new AffineTransform();
    private Rectangle2D bbox;
    private Shapes shapes;
    // Graphics state object to be used by content parser
    private GraphicsState graphicsState;
    private ExtGState extGState;
    private Resources parentResource;
    // transparency grouping data
    private boolean transparencyGroup;
    private boolean isolated;
    private boolean knockOut;
    private boolean shading;
    private boolean inited = false;

    public Form(Library l, DictionaryEntries h, byte[] rawBytes) {
        super(l, h, rawBytes);

        // check for grouping flags so we can do special handling during the
        // xform content stream parsing.
        DictionaryEntries group = library.getDictionary(entries, GROUP_KEY);
        if (group != null) {
            transparencyGroup = true;
            isolated = library.getBoolean(group, I_KEY);
            knockOut = library.getBoolean(group, K_KEY);
        }
    }

    public DictionaryEntries getGroup() {
        return library.getDictionary(entries, GROUP_KEY);
    }

    public void setAppearance(Shapes shapes, AffineTransform matrix, Rectangle2D bbox) {
        inited = false;
        this.shapes = shapes;
        this.matrix = matrix;
        this.bbox = bbox;
        entries.put(Form.BBOX_KEY, PRectangle.getPRectangleVector(bbox));
        entries.put(Form.MATRIX_KEY, matrix);
    }

    /**
     * Sets the GraphicsState which should be used by the content parser when
     * parsing the Forms content stream.  The GraphicsState should be set
     * before init() is called, or it will have not effect on the rendered
     * content.
     *
     * @param graphicsState current graphic state
     */
    public void setGraphicsState(GraphicsState graphicsState) {
        if (graphicsState != null) {
            this.graphicsState = graphicsState;
            this.extGState = graphicsState.getExtGState();
        }
    }

    /**
     * Gets the associated graphic state instance for this form.
     *
     * @return external graphic state,  can be null.
     */
    public GraphicsState getGraphicsState() {
        return graphicsState;
    }

    /**
     * Gets the extended graphics state for the form at the time of creation.  This contains any masking and blending
     * data that might bet over written during the forms parsing.
     *
     * @return extended graphic state at the time of creation.
     */
    public ExtGState getExtGState() {
        return extGState;
    }

    /**
     * Utility method for parsing a vector of affinetranform values to an
     * affine transform.
     *
     * @param v vectory containing affine transform values.
     * @return affine tansform based on v
     */
    private static AffineTransform getAffineTransform(List v) {
        float[] f = new float[6];
        for (int i = 0; i < 6; i++) {
            f[i] = ((Number) v.get(i)).floatValue();
        }
        return new AffineTransform(f);
    }

    /**
     * As of the PDF 1.2 specification, a resource entry is not required for
     * a XObject, thus it needs to point to the parent resource to enable
     * to correctly load the content stream.
     *
     * @param parentResource parent objects resourse when available.
     */
    public void setParentResources(Resources parentResource) {
        this.parentResource = parentResource;
    }


    public synchronized void init() throws InterruptedException {
        init(null);
    }

    /**
     *
     */
    public synchronized void init(ContentStreamCallback contentStreamRedactorCallback)
            throws InterruptedException {
        if (inited) {
            return;
        }
        Object v = library.getObject(entries, MATRIX_KEY);
        if (v instanceof List) {
            matrix = getAffineTransform((List) v);
        } else if (v instanceof AffineTransform) {
            matrix = (AffineTransform) v;
        }
        bbox = library.getRectangle(entries, BBOX_KEY);
        // try and find the form's resources dictionary.
        Resources leafResources = library.getResources(entries, RESOURCES_KEY);
        // apply parent resource, if the current resources is null
        if (leafResources == null) {
            leafResources = parentResource;
        }
        // Build a new content parser for the content streams and apply the
        // content stream of the calling content stream.
        ContentParser cp = new ContentParser(library, leafResources, contentStreamRedactorCallback);
        cp.setGraphicsState(graphicsState);
        byte[] in = getDecodedStreamBytes();
        if (in != null) {
            try {
                logger.log(Level.FINER, () -> "Parsing form " + getPObjectReference());
                shapes = cp.parse(Stream.fromByteArray(in, this), null).getShapes();
                inited = true;
            } catch (InterruptedException e) {
                // the initialization was interrupted so, we need to make sure we bubble up the exception
                // as we need to let any chained forms know so, we can invalidate the page correctly
                shapes = new Shapes();
                logger.log(Level.FINE, "Parsing form interrupted parsing Form content stream.", e);
                throw new InterruptedException(e.getMessage());
            } catch (Exception e) {
                // some parsing or otherwise unrecoverable problem, we'll try to render the page nonetheless.
                // but not this form object.
                shapes = new Shapes();
                logger.log(Level.WARNING, "Error parsing Form content stream.", e);
            }
        }
    }

    public Resources getResources() {
        Resources leafResources = library.getResources(entries, RESOURCES_KEY);
        if (leafResources == null) {
            leafResources = new Resources(library, new DictionaryEntries());
        }
        return leafResources;
    }

    public void setResources(Resources resources) {
        entries.put(RESOURCES_KEY, resources.getEntries());
    }

    /**
     * Add a Font resource to this Form's resource dictionary.  If there is already a font resource of the same
     * name, it will be removed and replaced with the new font resource.  This is to ensure that we don't orphan font
     * resources in the document if the font was changed.
     * This is intended for new object only, can't guarantee this method will work as expected on an existing object.
     */
    public void addFontResource(Name fontName, Reference reference) {
        StateManager stateManager = library.getStateManager();
        Resources formResources = getResources();
        DictionaryEntries fontsDictionary = formResources.getFonts();
        if (fontsDictionary == null) {
            fontsDictionary = new DictionaryEntries();
            formResources.entries.put(FONT_KEY, fontsDictionary);
        }
        // remove previous font resource if it exists, this is to ensure that we don't orphan font resources in the
        // document if the font was changed.
        if (fontsDictionary.containsKey(EMBEDDED_FONT_NAME)) {
            Reference oldFontReference = (Reference) fontsDictionary.get(EMBEDDED_FONT_NAME);
            ContentWriterUtils.removeSimpleFont(library, oldFontReference);
        }
        fontsDictionary.put(fontName, reference);
        entries.put(RESOURCES_KEY, formResources.entries);

        // make sure the form changes get picked up as well
        stateManager.addTempChange(new PObject(this, getPObjectReference()));
    }

    public boolean hasFontResource(Name fontName) {
        Resources formResources = getResources();
        DictionaryEntries fontsDictionary = formResources.getFonts();
        if (fontsDictionary != null) {
            return fontsDictionary.get(fontName) != null;
        }
        return false;
    }

    public Reference getFontResource(Name fontName) {
        Resources formResources = getResources();
        DictionaryEntries fontsDictionary = formResources.getFonts();
        if (fontsDictionary != null) {
            return (Reference) fontsDictionary.get(fontName);
        }
        return null;
    }

    /**
     * Add an ImageStream to this Form's resource dictionary.  This is intended for new object only, can't guarantee
     * this method will work as expected on an existing object.
     *
     * @param imageName   named image
     * @param imageStream corresponding ImageStream data
     */
    public void addImageResource(Name imageName, ImageStream imageStream) {
        Resources formResources = getResources();
        DictionaryEntries xObjectsDictionary = formResources.getXObjects();
        if (xObjectsDictionary == null) {
            xObjectsDictionary = new DictionaryEntries();
            formResources.entries.put(XOBJECT_KEY, xObjectsDictionary);
        }
        // sync form resources with form object.
        entries.put(RESOURCES_KEY, formResources.entries);
        xObjectsDictionary.put(imageName, imageStream.getPObjectReference());
        StateManager stateManager = library.getStateManager();
        stateManager.addChange(new PObject(this, getPObjectReference()), isNew);
    }

    /**
     * Gets the shapes that where parsed from the content stream.
     *
     * @return shapes object for xObject.
     */
    public Shapes getShapes() {
        return shapes;
    }

    /**
     * Gets the bounding box for the xObject.
     *
     * @return rectangle in PDF coordinate space representing xObject bounds.
     */
    public Rectangle2D getBBox() {
        return bbox;
    }

    /**
     * Gets the optional matrix which describes how to convert the coordinate
     * system in xObject space to the parent coordinates space.
     *
     * @return affine transform representing the xObject's pdf to xObject space
     * transform.
     */
    public AffineTransform getMatrix() {
        return matrix;
    }

    /**
     * If the xObject has a transparency group flag.
     *
     * @return true if a transparency group exists, false otherwise.
     */
    public boolean isTransparencyGroup() {
        return transparencyGroup;
    }

    /**
     * Only present if a transparency group is present.  Isolated groups are
     * composed on a fully transparent back drop rather then the groups.
     *
     * @return true if the transparency group is isolated.
     */
    public boolean isIsolated() {
        return isolated;
    }

    /**
     * Only present if a transparency group is present.  Knockout groups individual
     * elements composed with the groups initial back drop rather then the stack.
     *
     * @return true if the transparency group is a knockout.
     */
    public boolean isKnockOut() {
        return knockOut;
    }

    public boolean isShading() {
        return shading;
    }

    public void setShading(boolean shading) {
        this.shading = shading;
    }
}
