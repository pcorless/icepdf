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
 * ICEpdf for JavaFX, in two layers.
 * <p>
 * The components, for an application that builds its own window:
 * <ul>
 *     <li>{@code org.icepdf.fx.view}: {@code PdfView}, the embeddable document control, and its events;</li>
 *     <li>{@code org.icepdf.fx.panels}: side panels for a {@code PdfView} (pages, bookmarks, comments,
 *     attachments, layers, signatures, search, properties);</li>
 *     <li>{@code org.icepdf.fx.print}, {@code org.icepdf.fx.signature}: printing, signature checks and
 *     signing, each with its dialog;</li>
 *     <li>{@code org.icepdf.fx.fonts}: the system font scan core draws with.</li>
 * </ul>
 * The reference viewer built from them ({@code ri}), for an application that wants a whole viewer:
 * <ul>
 *     <li>{@code org.icepdf.fx.ri}: {@code PdfViewer}, the viewer component, and how it is configured
 *     ({@code ViewerFeatures}, {@code UserLayout}, {@code Theme}, the stores it remembers things in);</li>
 *     <li>{@code org.icepdf.fx.ri.actions} and its sub-packages: the viewer's commands, as actions an
 *     application's own menus and tool bars can be built from;</li>
 *     <li>{@code org.icepdf.fx.ri.document}: one open document - opening, saving, passwords, signing;</li>
 *     <li>{@code org.icepdf.fx.ri.prefs}: the stand-alone viewer's settings file and preferences dialog;</li>
 *     <li>{@code org.icepdf.fx.ri.icons}: the icon set, replaceable.</li>
 * </ul>
 * The rendering engine behind {@code PdfView} and the viewer's own chrome ({@code ri.ui}) are not
 * exported.
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
    exports org.icepdf.fx.fonts;
    exports org.icepdf.fx.ri;
    exports org.icepdf.fx.ri.actions;
    exports org.icepdf.fx.ri.actions.app;
    exports org.icepdf.fx.ri.actions.document;
    exports org.icepdf.fx.ri.actions.edit;
    exports org.icepdf.fx.ri.actions.forms;
    exports org.icepdf.fx.ri.actions.navigation;
    exports org.icepdf.fx.ri.actions.search;
    exports org.icepdf.fx.ri.actions.tools;
    exports org.icepdf.fx.ri.actions.view;
    exports org.icepdf.fx.ri.document;
    exports org.icepdf.fx.ri.prefs;
    exports org.icepdf.fx.ri.icons;
    exports org.icepdf.fx.ri.app to javafx.graphics;
}
