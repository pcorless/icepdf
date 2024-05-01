module org.icepdf.ri.viewer {
    requires java.logging;
    requires java.prefs;
    requires java.desktop;
    requires java.net.http;

    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.util;

    // viewer ri api
    exports org.icepdf.ri.common;
    exports org.icepdf.ri.images;
    exports org.icepdf.ri.util;
    exports org.icepdf.ri.viewer;
    requires org.icepdf.core;
}