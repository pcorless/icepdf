/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.qa.utilities;

import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class CaptureLogTest {

    @Test
    public void logsTaggedRecordsAndStderrAndSummarises() throws Exception {
        Path results = Files.createTempDirectory("qa-capture-log");
        PrintStream err = System.err;
        CaptureLog log = CaptureLog.start(results, "test run");
        assertNotNull(log);
        Logger logger = Logger.getLogger("org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory");
        try {
            CaptureLog.setContext("A: out.pdf p3");
            logger.log(Level.SEVERE, "Error while getting predefined CMap",
                    new IOException("Could not find referenced cmap stream Adobe-UCS-UCS2"));
            logger.info("below the threshold, not logged");
            System.err.println("a direct stderr line");
            CaptureLog.clearContext();

            CaptureLog.setContext("B: other.pdf p1");
            logger.log(Level.SEVERE, "Error while getting predefined CMap",
                    new IOException("Could not find referenced cmap stream Adobe-UCS-UCS2"));
            CaptureLog.clearContext();

            // an untagged thread, e.g. an ICEpdf pool thread, reports what was in flight
            CaptureLog.setContext("A: busy.pdf p2");
            Thread pool = new Thread(() -> logger.warning("decode failed"), "ICEpdf-thread-image-pool-1");
            pool.start();
            pool.join();
            CaptureLog.clearContext();
        } finally {
            log.finish();
        }
        assertSame("System.err must be restored", err, System.err);

        String text = new String(Files.readAllBytes(log.getLogFile()), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("[A: out.pdf p3] SEVERE " + logger.getName()));
        assertTrue("stack trace in the log", text.contains("java.io.IOException: Could not find referenced cmap"));
        assertTrue("stderr copied with context", text.contains("[A: out.pdf p3] stderr: a direct stderr line"));
        assertFalse("INFO is below the default threshold", text.contains("below the threshold"));
        assertTrue("pool thread names the documents in flight",
                text.contains("[ICEpdf-thread-image-pool-1; in flight: A: busy.pdf p2] WARNING"));

        Path summary = log.getLogFile().resolveSibling(
                log.getLogFile().getFileName().toString().replace(".log", "-summary.txt"));
        String summaryText = new String(Files.readAllBytes(summary), StandardCharsets.UTF_8);
        assertTrue(summaryText, summaryText.contains("2 distinct problem(s), 1 line(s) of System.err"));
        assertTrue(summaryText, summaryText.contains("2 x SEVERE " + logger.getName()));
        assertTrue(summaryText, summaryText.contains("e.g. A: out.pdf p3 | B: other.pdf p1"));
    }

    @Test
    public void normaliseGroupsDumpsReferencesAndValues() {
        String a = CaptureLog.normalise("Missing appearance for ANNOTATION= {Type=Annot, Rect=[1.0, 2.0], AP={N=12 0 R}} 8 0 R");
        String b = CaptureLog.normalise("Missing appearance for ANNOTATION= {Type=Annot, Rect=[9.5, 3.0], AP={N=99 0 R}} 41 0 R");
        assertEquals(a, b);
        assertEquals(CaptureLog.normalise("could not find \"Arial\""), CaptureLog.normalise("could not find \"Helv\""));
    }

    @Test
    public void summaryLeadsWithIcepdfExceptionsAndRollsUpFontBox() throws Exception {
        Path results = Files.createTempDirectory("qa-capture-log-summary");
        CaptureLog log = CaptureLog.start(results, "summary run");
        assertNotNull(log);
        try {
            CaptureLog.setContext("A: x.pdf p1");
            // many FontBox records, one ICEpdf-thrown exception (via a JDK frame), one plain warning
            Logger fontBox = Logger.getLogger("org.apache.fontbox.ttf.GlyphSubstitutionTable");
            for (int i = 0; i < 5; i++) {
                fontBox.warning("lookupListOffset is 0, LookupListTable is considered empty " + i);
            }
            Logger.getLogger("org.icepdf.core.pobjects.fonts.zfont.SimpleFont").warning("plain warning");
            IllegalArgumentException thrown = new IllegalArgumentException("Color parameter outside of expected range");
            thrown.setStackTrace(new StackTraceElement[]{
                    new StackTraceElement("java.awt.Color", "testColorValueRange", "Color.java", 310),
                    new StackTraceElement("org.icepdf.core.pobjects.graphics.Separation", "getColor", "Separation.java", 176)});
            Logger.getLogger("org.icepdf.core.pobjects.graphics.ShadingType2Pattern")
                    .log(Level.WARNING, "Could not initialize type 2 shading", thrown);
            CaptureLog.clearContext();
        } finally {
            log.finish();
        }
        Path summaryFile = log.getLogFile().resolveSibling(
                log.getLogFile().getFileName().toString().replace(".log", "-summary.txt"));
        String summary = new String(Files.readAllBytes(summaryFile), StandardCharsets.UTF_8);
        int icepdf = summary.indexOf("-- Exceptions thrown from ICEpdf code --");
        int shading = summary.indexOf("ShadingType2Pattern");
        int rest = summary.indexOf("-- Everything else --");
        int fontBoxSection = summary.indexOf("-- FontBox (5 records, by logger) --");
        assertTrue(summary, icepdf >= 0 && shading > icepdf && rest > shading && fontBoxSection > rest);
        assertTrue(summary, summary.contains("5 x org.apache.fontbox.ttf.GlyphSubstitutionTable (1 distinct)"));
        assertFalse("FontBox records are not listed individually",
                summary.substring(0, fontBoxSection).contains("lookupListOffset"));
    }
}
