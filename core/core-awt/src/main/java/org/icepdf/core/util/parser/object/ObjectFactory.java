package org.icepdf.core.util.parser.object;


import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.graphics.Pattern;
import org.icepdf.core.pobjects.graphics.TilingPattern;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.core.pobjects.structure.CrossReferenceStream;
import org.icepdf.core.util.Library;

import java.nio.ByteBuffer;

/**
 *
 */
public class ObjectFactory {
    @SuppressWarnings("unchecked")
    public static PObject getInstance(Library library, int objectNumber, int generationNumber,
                                      Object objectData, ByteBuffer streamData) {
        // if we have as a byteBuffer then we have a stream.
        if (streamData != null) {
            DictionaryEntries entries = (DictionaryEntries) objectData;
            Name type = (Name) entries.get(Dictionary.TYPE_KEY);
            Name subType = (Name) entries.get(Dictionary.SUBTYPE_KEY);
            // todo come back an eval if we want byteBuffers or not as there is shit ton of refactoring work otherwise.
            byte[] bufferBytes = new byte[streamData.remaining()];
            streamData.get(bufferBytes);
            if (CrossReferenceStream.TYPE.equals(type)) {
                return new PObject(new CrossReferenceStream(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (ObjectStream.TYPE.equals(type)) {
                return new PObject(new ObjectStream(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (Form.TYPE_VALUE.equals(type) && ImageStream.TYPE_VALUE.equals(subType)) {
                return new PObject(new ImageStream(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (Form.TYPE_VALUE.equals(type)) {
                return new PObject(new Form(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (Pattern.TYPE_VALUE.equals(type)) {
                return new PObject(new TilingPattern(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (ImageStream.TYPE_VALUE.equals(subType)) {
                return new PObject(new ImageStream(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (Form.SUB_TYPE_VALUE.equals(subType) && !TilingPattern.TYPE_VALUE.equals(type)) {
                return new PObject(new Form(library, entries, bufferBytes), objectNumber, generationNumber);
            } else if (TilingPattern.TYPE_VALUE.equals(subType) && TilingPattern.TYPE_VALUE.equals(type)) {
                return new PObject(new TilingPattern(library, entries, bufferBytes), objectNumber, generationNumber);
            }

            return new PObject(new Stream(library, entries, bufferBytes), objectNumber, generationNumber);

        } else if (objectData instanceof DictionaryEntries) {
            DictionaryEntries entries = (DictionaryEntries) objectData;
            Name type = library.getName(entries, Dictionary.TYPE_KEY);
            if (type != null) {
                if (Catalog.TYPE.equals(type)) {
                    return new PObject(new Catalog(library, entries), objectNumber, generationNumber);
                } else if (PageTree.TYPE.equals(type)) {
                    return new PObject(new PageTree(library, entries), objectNumber, generationNumber);
                } else if (Page.TYPE.equals(type)) {
                    return new PObject(new Page(library, entries), objectNumber, generationNumber);
                } else if (Font.TYPE.equals(type)) {
                    // do a quick check to make sure we don't have a fontDescriptor
                    // FontFile is specific to font descriptors.
                    boolean fontDescriptor = entries.get(FontDescriptor.FONT_FILE) != null ||
                            entries.get(FontDescriptor.FONT_FILE_2) != null ||
                            entries.get(FontDescriptor.FONT_FILE_3) != null;
                    if (fontDescriptor) {
                        return new PObject(new FontDescriptor(library, entries), objectNumber, generationNumber);
                    } else {
                        return new PObject(
                                FontFactory.getInstance().getFont(library, entries), objectNumber, generationNumber);
                    }
                } else if (FontDescriptor.TYPE.equals(type)) {
                    return new PObject(new FontDescriptor(library, entries), objectNumber, generationNumber);
                } else if (Annotation.TYPE.equals(type)) {
                    return new PObject(Annotation.buildAnnotation(library, entries), objectNumber, generationNumber);
                } else if (CMap.TYPE.equals(type)) {
                    return new PObject(entries, objectNumber, generationNumber);
                } else if (OptionalContentGroup.TYPE.equals(type)) {
                    return new PObject(new OptionalContentGroup(library, entries), objectNumber, generationNumber);
                } else if (OptionalContentMembership.TYPE.equals(type)) {
                    return new PObject(new OptionalContentMembership(library, entries), objectNumber, generationNumber);
                } else {
                    return new PObject(entries, objectNumber, generationNumber);
                }

            }
        }
        return new PObject(objectData, objectNumber, generationNumber);
    }
}
