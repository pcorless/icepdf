/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects.fonts.zfont.cmap;


import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.cmap.CMapParser;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for creating CMaps, either predefined or embedded.
 * <p>
 * This class provides methods to retrieve predefined CMaps by name,
 * parse embedded CMaps from streams, and parse CID to GID maps.
 * It also caches predefined CMaps for efficiency.
 */
public class CMapFactory {

    private static final Logger logger =
            Logger.getLogger(CMapFactory.class.toString());

    public static final Name TYPE = new Name("CMap");

    public static final Name IDENTITY_NAME = new Name("Identity");
    public static final Name IDENTITY_V_NAME = new Name("Identity-V");
    public static final Name IDENTITY_H_NAME = new Name("Identity-H");

    private static final Map<String, CMap> CMAP_CACHE = new ConcurrentHashMap<>();

    private CMapFactory() {
    }

    public static CMap getPredefinedCMap(Name cMapName) {
        return getPredefinedCMap(cMapName.getName());
    }

    public static CMap getPredefinedCMap(String cMapName) {
        CMap cmap = CMAP_CACHE.get(cMapName);
        if (cmap != null) {
            return cmap;
        }

        CMap targetCmap = null;
        try {
            targetCmap = new CMapParser().parsePredefined(cMapName);
            // limit the cache to predefined CMaps
            CMAP_CACHE.put(targetCmap.getName(), targetCmap);
            return targetCmap;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while getting predefined CMap", e);
            return null;
        }
    }

    public static CMap parseEmbeddedCMap(Stream stream) throws IOException {
        return new CMapParser().parse(new RandomAccessReadBuffer(stream.getDecodedByteArrayInputStream()));
    }

    /**
     * Parses a CID to GID map from the provided CMap stream.
     * The method reads the stream, extracting character IDs and their corresponding glyph IDs.
     *
     * @param cMapStream the CMap stream containing the CID to GID mapping.
     * @return an array of integers representing the CID to GID mapping, or null if an error occurs.
     */
    public static int[] parseCidToGidMap(Stream cMapStream) {
        try (ByteArrayInputStream cidStream = cMapStream.getDecodedByteArrayInputStream()) {
            int character = 0;
            int i = 0;
            int length = cidStream.available() / 2;
            int[] cidToGid = new int[length];
            // parse the cidToGid stream out, arranging the high bit,
            // each character position that has a value > 0 is a valid
            // entry in the CFF.
            while (i < length) {
                character = cidStream.read();
                character = (char) ((character << 8) | cidStream.read());
                cidToGid[i] = (char) character;
                i++;
            }
            return cidToGid;
        } catch (IOException e) {
            logger.log(Level.FINE, "Error reading CIDToGIDMap Stream.", e);
        }
        return null;
    }
}
