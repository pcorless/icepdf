/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-662: how a remote document is fetched can be tuned by system property, for a network, without code; the
 * builder still has the last word.
 */
public class UrlLoadOptionsTest {

    private static final String[] PROPERTIES = {UrlLoadOptions.RANGE_REQUESTS_PROPERTY,
            UrlLoadOptions.CHUNK_SIZE_PROPERTY, UrlLoadOptions.BLOCK_SIZE_PROPERTY, UrlLoadOptions.PROBE_SIZE_PROPERTY,
            UrlLoadOptions.MAX_CONCURRENT_REQUESTS_PROPERTY, UrlLoadOptions.THROTTLE_LATENCY_PROPERTY,
            UrlLoadOptions.THROTTLE_BANDWIDTH_PROPERTY};

    @AfterEach
    void clear() {
        for (String property : PROPERTIES) {
            System.clearProperty(property);
        }
    }

    @Test
    public void theDefaults() {
        UrlLoadOptions options = UrlLoadOptions.defaults();
        assertTrue(options.isRangeRequests());
        assertEquals(64 * 1024, options.getChunkSize());
        assertEquals(2048, options.getBlockSize());
        assertEquals(512, options.getProbeSize());
        assertEquals(4, options.getMaxConcurrentRequests());
        assertNull(options.getThrottle());
    }

    @Test
    public void throttlePropertiesGiveEveryDocumentAThrottle() {
        System.setProperty(UrlLoadOptions.THROTTLE_LATENCY_PROPERTY, "150");
        System.setProperty(UrlLoadOptions.THROTTLE_BANDWIDTH_PROPERTY, "200");
        NetworkThrottle throttle = UrlLoadOptions.defaults().getThrottle();
        assertNotNull(throttle);
        assertEquals(150, throttle.getLatency().toMillis());
        assertEquals(200 * 1024, throttle.getBytesPerSecond());
        assertNull(UrlLoadOptions.builder().throttle(null).build().getThrottle());
    }

    @Test
    public void propertiesSetTheDefaults() {
        System.setProperty(UrlLoadOptions.RANGE_REQUESTS_PROPERTY, "false");
        System.setProperty(UrlLoadOptions.CHUNK_SIZE_PROPERTY, "131072");
        System.setProperty(UrlLoadOptions.BLOCK_SIZE_PROPERTY, "8192");
        System.setProperty(UrlLoadOptions.PROBE_SIZE_PROPERTY, "4096");
        System.setProperty(UrlLoadOptions.MAX_CONCURRENT_REQUESTS_PROPERTY, "2");
        UrlLoadOptions options = UrlLoadOptions.defaults();
        assertFalse(options.isRangeRequests());
        assertEquals(131072, options.getChunkSize());
        assertEquals(8192, options.getBlockSize());
        assertEquals(4096, options.getProbeSize());
        assertEquals(2, options.getMaxConcurrentRequests());
        // the builder overrides a property
        assertEquals(1024, UrlLoadOptions.builder().blockSize(1024).build().getBlockSize());
    }

    @Test
    public void aPropertyOutOfRangeIsBroughtIntoRange() {
        System.setProperty(UrlLoadOptions.BLOCK_SIZE_PROPERTY, "10");
        System.setProperty(UrlLoadOptions.MAX_CONCURRENT_REQUESTS_PROPERTY, "0");
        System.setProperty(UrlLoadOptions.PROBE_SIZE_PROPERTY, "lots");
        UrlLoadOptions options = UrlLoadOptions.defaults();
        assertEquals(256, options.getBlockSize());
        assertEquals(1, options.getMaxConcurrentRequests());
        assertEquals(512, options.getProbeSize(), "not a number: the default");
        // nothing bigger than a chunk
        System.setProperty(UrlLoadOptions.CHUNK_SIZE_PROPERTY, "4096");
        System.setProperty(UrlLoadOptions.BLOCK_SIZE_PROPERTY, "65536");
        assertEquals(4096, UrlLoadOptions.defaults().getBlockSize());
    }

    @Test
    public void theBuilderRefusesWhatsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> UrlLoadOptions.builder().blockSize(100));
        assertThrows(IllegalArgumentException.class, () -> UrlLoadOptions.builder().probeSize(10));
        assertThrows(IllegalArgumentException.class, () -> UrlLoadOptions.builder().maxConcurrentRequests(0));
        assertThrows(IllegalArgumentException.class, () -> UrlLoadOptions.builder().chunkSize(512));
    }
}
