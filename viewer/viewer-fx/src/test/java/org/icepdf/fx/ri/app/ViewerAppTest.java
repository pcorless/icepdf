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
package org.icepdf.fx.ri.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Command-line arguments and link schemes, no toolkit. */
class ViewerAppTest {

    @DisplayName("an argument is a path or a file: URI; another URL or a blank is no file")
    @Test
    void argumentPaths() {
        assertEquals(Path.of("/tmp/a b.pdf"), ViewerApp.argumentPath("file:///tmp/a%20b.pdf"));
        assertEquals(Path.of("/tmp/x.pdf"), ViewerApp.argumentPath("/tmp/x.pdf"));
        assertEquals(Path.of("rel.pdf"), ViewerApp.argumentPath("rel.pdf"));
        assertNull(ViewerApp.argumentPath("https://example.com/x.pdf"));
        assertNull(ViewerApp.argumentPath("file://host:bad/%%"));
        assertNull(ViewerApp.argumentPath(" "));
        assertNull(ViewerApp.argumentPath("a\u0000b"));
    }

    @DisplayName("a link's scheme, lower case, even when the link isn't escaped; none for a relative link")
    @Test
    void linkSchemes() {
        assertEquals("https", ViewerApp.linkScheme("HTTPS://example.com"));
        assertEquals("http", ViewerApp.linkScheme("http://example.com/a b"));
        assertEquals("mailto", ViewerApp.linkScheme("mailto:someone@example.com"));
        assertEquals("file", ViewerApp.linkScheme("file:///etc/passwd"));
        assertEquals("smb", ViewerApp.linkScheme("smb://server/share"));
        assertNull(ViewerApp.linkScheme("other.pdf"));
        assertNull(ViewerApp.linkScheme("../docs/page.html"));
        assertNull(ViewerApp.linkScheme(null));
    }
}
