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
package org.icepdf.fx.signature;

/**
 * What {@link SignDialog} remembers from one signing to the next: where the signer's key is (a
 * PKCS#12 file or a PKCS#11 token), which certificate, the timestamp authority, the details and the
 * appearance, including a drawn signature.  Never the password or PIN.
 * <p>
 * A plain bean: the dialog starts from it and writes the signer's choices back on Sign; keeping it
 * between sessions is the application's job.
 */
public class SigningSettings {

    /** Where the signing key lives. */
    public enum KeystoreType {
        /** A certificate file, .p12 / .pfx. */
        PKCS12,
        /** A smart card or token, through a SunPKCS11 provider configuration file. */
        PKCS11
    }

    /** What the visible signature shows beside or under its text. */
    public enum ImageSource {
        NONE, DRAWN, FILE
    }

    private KeystoreType keystoreType = KeystoreType.PKCS12;
    private String pkcs12Path;
    private String pkcs11Config;
    private String certificateAlias;
    private String timestampAuthority;
    private String contact;
    private String location;
    private String reason;
    private boolean visible = true;
    private boolean showText = true;
    private ImageSource imageSource = ImageSource.NONE;
    private String imagePath;
    private SignatureAppearance.Layout layout = SignatureAppearance.Layout.SIDE_BY_SIDE;
    private int fontSize = 7;
    private final Ink ink = new Ink();

    public KeystoreType getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(KeystoreType keystoreType) {
        this.keystoreType = keystoreType != null ? keystoreType : KeystoreType.PKCS12;
    }

    public String getPkcs12Path() {
        return pkcs12Path;
    }

    public void setPkcs12Path(String pkcs12Path) {
        this.pkcs12Path = pkcs12Path;
    }

    /** The SunPKCS11 configuration file naming the token's library. */
    public String getPkcs11Config() {
        return pkcs11Config;
    }

    public void setPkcs11Config(String pkcs11Config) {
        this.pkcs11Config = pkcs11Config;
    }

    /** The keystore path for the current type. */
    public String getKeystorePath() {
        return keystoreType == KeystoreType.PKCS11 ? pkcs11Config : pkcs12Path;
    }

    /** The certificate last signed with, to select again. */
    public String getCertificateAlias() {
        return certificateAlias;
    }

    public void setCertificateAlias(String certificateAlias) {
        this.certificateAlias = certificateAlias;
    }

    public String getTimestampAuthority() {
        return timestampAuthority;
    }

    public void setTimestampAuthority(String timestampAuthority) {
        this.timestampAuthority = timestampAuthority;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isShowText() {
        return showText;
    }

    public void setShowText(boolean showText) {
        this.showText = showText;
    }

    public ImageSource getImageSource() {
        return imageSource;
    }

    public void setImageSource(ImageSource imageSource) {
        this.imageSource = imageSource != null ? imageSource : ImageSource.NONE;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public SignatureAppearance.Layout getLayout() {
        return layout;
    }

    public void setLayout(SignatureAppearance.Layout layout) {
        this.layout = layout != null ? layout : SignatureAppearance.Layout.SIDE_BY_SIDE;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    /** The drawn signature, edited in place by the dialog's pad. */
    public Ink getInk() {
        return ink;
    }
}
