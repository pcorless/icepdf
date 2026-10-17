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
package org.icepdf.fx.ri.prefs;

import org.icepdf.fx.signature.SignatureAppearance;
import org.icepdf.fx.signature.SigningSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SigningPreferencesTest {

    @TempDir
    Path temp;

    @DisplayName("the signing settings and the drawn signature come back in the next session")
    @Test
    void roundTrip() throws Exception {
        Path file = temp.resolve("viewer.properties");
        SigningPreferences store = SigningPreferences.of(ViewerPreferences.load(file));
        SigningSettings s = store.settings();
        s.setKeystoreType(SigningSettings.KeystoreType.PKCS11);
        s.setPkcs11Config("/etc/token.cfg");
        s.setPkcs12Path("/home/me/me.p12");
        s.setCertificateAlias("signing key");
        s.setTimestampAuthority("https://tsa.example");
        s.setReason("Approved");
        s.setImageSource(SigningSettings.ImageSource.DRAWN);
        s.setLayout(SignatureAppearance.Layout.OVERLAY);
        s.setFontSize(9);
        s.getInk().parse("1,2 3,4\n5,6");
        s.getInk().setColor(new Color(0x123456));
        s.getInk().setWidth(3.5);
        store.save(s);
        assertTrue(Files.isRegularFile(temp.resolve("signature.ink")));
        assertFalse(Files.readString(file).contains("changeit"));

        SigningSettings back = SigningPreferences.of(ViewerPreferences.load(file)).settings();
        assertEquals(SigningSettings.KeystoreType.PKCS11, back.getKeystoreType());
        assertEquals("/etc/token.cfg", back.getKeystorePath());
        assertEquals("/home/me/me.p12", back.getPkcs12Path());
        assertEquals("signing key", back.getCertificateAlias());
        assertEquals("https://tsa.example", back.getTimestampAuthority());
        assertEquals("Approved", back.getReason());
        assertEquals(SigningSettings.ImageSource.DRAWN, back.getImageSource());
        assertEquals(SignatureAppearance.Layout.OVERLAY, back.getLayout());
        assertEquals(9, back.getFontSize());
        assertEquals(s.getInk().format(), back.getInk().format());
        assertEquals(new Color(0x123456), back.getInk().getColor());
        assertEquals(3.5, back.getInk().getWidth());
    }

    @DisplayName("clearing the drawing removes the ink file")
    @Test
    void clearRemovesInk() {
        Path file = temp.resolve("viewer.properties");
        SigningPreferences store = SigningPreferences.of(ViewerPreferences.load(file));
        store.settings().getInk().parse("1,2 3,4");
        store.save(store.settings());
        store.settings().getInk().clear();
        store.save(store.settings());
        assertFalse(Files.exists(temp.resolve("signature.ink")));
    }
}
