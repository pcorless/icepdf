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

/**
 * ICEpdf for JavaFX.
 * <ul>
 *     <li>{@code org.icepdf.fx.view}: {@code PdfView}, the embeddable document control, and its events;</li>
 *     <li>{@code org.icepdf.fx.panels}: side panels for a {@code PdfView} (pages, bookmarks, comments,
 *     attachments, layers, signatures, search, properties);</li>
 *     <li>{@code org.icepdf.fx.print}, {@code org.icepdf.fx.signature}: printing, signature checks and signing;</li>
 *     <li>{@code org.icepdf.fx.viewer}: the viewer application.</li>
 * </ul>
 * The rendering engine behind {@code PdfView} is not exported.
 */
module org.icepdf.fx {
    requires transitive javafx.controls;
    requires transitive org.icepdf.core;
    requires java.desktop;
    requires java.logging;
    requires java.prefs;
    requires java.naming;
    // ASN.1 values shown in the signature properties dialog.
    requires org.bouncycastle.provider;

    exports org.icepdf.fx.view;
    exports org.icepdf.fx.panels;
    exports org.icepdf.fx.print;
    exports org.icepdf.fx.signature;
    exports org.icepdf.fx.viewer;
    // the demo is launched, not used: JavaFX needs to reach its Application class.
    exports org.icepdf.fx.demo to javafx.graphics;
}
