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

import org.icepdf.fx.ri.SigningStore;
import org.icepdf.fx.signature.SignatureAppearance;
import org.icepdf.fx.signature.SigningSettings;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the signing settings in the viewer's settings ({@code signing.*} keys) and the drawn
 * signature's strokes in a file beside them ({@code signature.ink}), so a signer types only the
 * password or PIN next time.  The password is never kept.
 */
public final class SigningPreferences implements SigningStore {

    private static final Logger logger = Logger.getLogger(SigningPreferences.class.getName());

    public static final String KEYSTORE_TYPE = "signing.keystore.type";
    public static final String PKCS12_PATH = "signing.keystore.pkcs12";
    public static final String PKCS11_CONFIG = "signing.keystore.pkcs11";
    public static final String CERTIFICATE = "signing.certificate";
    public static final String TSA = "signing.tsa";
    public static final String CONTACT = "signing.contact";
    public static final String LOCATION = "signing.location";
    public static final String REASON = "signing.reason";
    public static final String VISIBLE = "signing.visible";
    public static final String SHOW_TEXT = "signing.showText";
    public static final String IMAGE_SOURCE = "signing.image";
    public static final String IMAGE_PATH = "signing.image.path";
    public static final String LAYOUT = "signing.layout";
    public static final String FONT_SIZE = "signing.fontSize";
    public static final String INK_COLOR = "signing.ink.color";
    public static final String INK_WIDTH = "signing.ink.width";

    private final ViewerPreferences preferences;
    private final Path inkFile;
    private SigningSettings settings;

    /** @param inkFile where the drawn signature is kept; null keeps it in the settings only for this run */
    public SigningPreferences(ViewerPreferences preferences, Path inkFile) {
        this.preferences = preferences;
        this.inkFile = inkFile;
    }

    /** The store for the user's settings: the strokes in {@code signature.ink} in the settings directory. */
    public static SigningPreferences of(ViewerPreferences preferences) {
        return new SigningPreferences(preferences,
                preferences.getFile() != null ? preferences.getFile().resolveSibling("signature.ink") : null);
    }

    @Override
    public SigningSettings settings() {
        if (settings == null) settings = read();
        return settings;
    }

    private SigningSettings read() {
        SigningSettings s = new SigningSettings();
        s.setKeystoreType(preferences.getEnum(KEYSTORE_TYPE, SigningSettings.KeystoreType.class,
                SigningSettings.KeystoreType.PKCS12));
        s.setPkcs12Path(preferences.get(PKCS12_PATH, null));
        s.setPkcs11Config(preferences.get(PKCS11_CONFIG, null));
        s.setCertificateAlias(preferences.get(CERTIFICATE, null));
        s.setTimestampAuthority(preferences.get(TSA, null));
        s.setContact(preferences.get(CONTACT, null));
        s.setLocation(preferences.get(LOCATION, null));
        s.setReason(preferences.get(REASON, null));
        s.setVisible(preferences.getBoolean(VISIBLE, true));
        s.setShowText(preferences.getBoolean(SHOW_TEXT, true));
        s.setImageSource(preferences.getEnum(IMAGE_SOURCE, SigningSettings.ImageSource.class,
                SigningSettings.ImageSource.NONE));
        s.setImagePath(preferences.get(IMAGE_PATH, null));
        s.setLayout(preferences.getEnum(LAYOUT, SignatureAppearance.Layout.class, SignatureAppearance.Layout.SIDE_BY_SIDE));
        s.setFontSize((int) Math.max(4, Math.min(36, preferences.getDouble(FONT_SIZE, 7))));
        String color = preferences.get(INK_COLOR, null);
        if (color != null) {
            try {
                s.getInk().setColor(new Color(Integer.parseInt(color.trim()) & 0xFFFFFF));
            } catch (NumberFormatException ignored) {
                // a hand-edited bad value keeps the default
            }
        }
        s.getInk().setWidth(preferences.getDouble(INK_WIDTH, s.getInk().getWidth()));
        if (inkFile != null && Files.isRegularFile(inkFile)) {
            try {
                s.getInk().parse(Files.readString(inkFile, StandardCharsets.UTF_8));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not read the drawn signature " + inkFile, e);
            }
        }
        return s;
    }

    @Override
    public void save(SigningSettings s) {
        settings = s;
        preferences.putEnum(KEYSTORE_TYPE, s.getKeystoreType());
        preferences.put(PKCS12_PATH, s.getPkcs12Path());
        preferences.put(PKCS11_CONFIG, s.getPkcs11Config());
        preferences.put(CERTIFICATE, s.getCertificateAlias());
        preferences.put(TSA, s.getTimestampAuthority());
        preferences.put(CONTACT, s.getContact());
        preferences.put(LOCATION, s.getLocation());
        preferences.put(REASON, s.getReason());
        preferences.putBoolean(VISIBLE, s.isVisible());
        preferences.putBoolean(SHOW_TEXT, s.isShowText());
        preferences.putEnum(IMAGE_SOURCE, s.getImageSource());
        preferences.put(IMAGE_PATH, s.getImagePath());
        preferences.putEnum(LAYOUT, s.getLayout());
        preferences.putDouble(FONT_SIZE, s.getFontSize());
        preferences.put(INK_COLOR, String.valueOf(s.getInk().getColor().getRGB() & 0xFFFFFF));
        preferences.putDouble(INK_WIDTH, s.getInk().getWidth());
        preferences.save();
        if (inkFile == null) return;
        try {
            if (s.getInk().isEmpty()) {
                Files.deleteIfExists(inkFile);
            } else {
                Files.createDirectories(inkFile.getParent());
                Files.writeString(inkFile, s.getInk().format(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save the drawn signature to " + inkFile, e);
        }
    }
}
