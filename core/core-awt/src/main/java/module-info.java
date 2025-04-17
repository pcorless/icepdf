module org.icepdf.core {
    requires java.logging;
    requires java.prefs;
    requires java.desktop;
    requires java.net.http;
    requires java.naming;

    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.util;

    // automatic module names
    requires org.apache.fontbox;
    requires org.apache.pdfbox.io;
    requires org.apache.pdfbox.jbig2;
    requires org.apache.commons.logging;
    requires com.twelvemonkeys.common.image;
    requires com.twelvemonkeys.common.io;
    requires com.twelvemonkeys.common.lang;
    requires com.twelvemonkeys.imageio.core;
    requires com.twelvemonkeys.imageio.metadata;
    requires com.twelvemonkeys.imageio.tiff;

    // core api
    exports org.icepdf.core;
    exports org.icepdf.core.application;
    exports org.icepdf.core.events;
    exports org.icepdf.core.exceptions;
    exports org.icepdf.core.io;
    exports org.icepdf.core.pobjects;
    exports org.icepdf.core.pobjects.acroform;
    exports org.icepdf.core.pobjects.acroform.signature;
    exports org.icepdf.core.pobjects.acroform.signature.appearance;
    exports org.icepdf.core.pobjects.acroform.signature.certificates;
    exports org.icepdf.core.pobjects.acroform.signature.exceptions;
    exports org.icepdf.core.pobjects.acroform.signature.handlers;
    exports org.icepdf.core.pobjects.acroform.signature.utils;
    exports org.icepdf.core.pobjects.actions;
    exports org.icepdf.core.pobjects.annotations;
    exports org.icepdf.core.pobjects.annotations.utils;
    exports org.icepdf.core.pobjects.fonts;
    exports org.icepdf.core.pobjects.graphics;
    exports org.icepdf.core.pobjects.graphics.commands;
    exports org.icepdf.core.pobjects.graphics.images;
    exports org.icepdf.core.pobjects.graphics.images.references;
    exports org.icepdf.core.pobjects.graphics.text;
    exports org.icepdf.core.pobjects.security;
    exports org.icepdf.core.pobjects.structure;
    exports org.icepdf.core.search;
    exports org.icepdf.core.util;
    exports org.icepdf.core.util.edit.content;
    exports org.icepdf.core.util.loggers;
    exports org.icepdf.core.util.updater;

}