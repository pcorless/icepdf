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
}
