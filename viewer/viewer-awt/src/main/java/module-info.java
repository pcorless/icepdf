module org.icepdf.ri.viewer {
    requires java.logging;
    requires java.prefs;
    requires java.desktop;
    requires java.net.http;

    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.util;

    requires org.icepdf.core;
    requires org.apache.fontbox;

    // viewer ri api
    exports org.icepdf.ri.common;
    exports org.icepdf.ri.common.fonts;
    exports org.icepdf.ri.common.preferences;
    exports org.icepdf.ri.common.print;
    exports org.icepdf.ri.common.properties;
    exports org.icepdf.ri.common.search;
    exports org.icepdf.ri.common.tools;
    exports org.icepdf.ri.common.utility.annotation;
    exports org.icepdf.ri.common.utility.annotation.acroform;
    exports org.icepdf.ri.common.utility.annotation.destinations;
    exports org.icepdf.ri.common.utility.annotation.markup;
    exports org.icepdf.ri.common.utility.annotation.properties;
    exports org.icepdf.ri.common.utility.attachment;
    exports org.icepdf.ri.common.utility.layers;
    exports org.icepdf.ri.common.utility.outline;
    exports org.icepdf.ri.common.utility.search;
    exports org.icepdf.ri.common.utility.signatures;
    exports org.icepdf.ri.common.utility.thumbs;
    exports org.icepdf.ri.common.views;
    exports org.icepdf.ri.common.views.annotations;
    exports org.icepdf.ri.common.views.annotations.acroform;
    exports org.icepdf.ri.common.views.annotations.signatures;
    exports org.icepdf.ri.common.views.annotations.summary;
    exports org.icepdf.ri.common.views.destinations;
    exports org.icepdf.ri.common.views.listeners;
    exports org.icepdf.ri.common.widgets;
    exports org.icepdf.ri.common.widgets.annotations;
    exports org.icepdf.ri.images;
    exports org.icepdf.ri.util;
    exports org.icepdf.ri.util.font;
    exports org.icepdf.ri.util.qa;
    exports org.icepdf.ri.viewer;

}