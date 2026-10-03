/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.structure.exceptions.ObjectStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * startxref (or a hybrid file's /XRefStm) that points at an object which isn't a cross-reference stream used to fail
 * a blind cast with ClassCastException (GH-263.Searching.Issue.pdf).  The main pointer now reports a structural
 * problem the reindex path expects; a bad /XRefStm is skipped, since it only supplements a valid table.
 */
public class CrossReferenceStreamPointerTest {

    private final List<LogRecord> warnings = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private final Logger documentLogger = Logger.getLogger(Document.class.getName());

    @BeforeEach
    public void capture() {
        documentLogger.addHandler(capture);
    }

    @AfterEach
    public void release() {
        documentLogger.removeHandler(capture);
    }

    @DisplayName("startxref at a plain object reindexes on a structural error, not a ClassCastException")
    @Test
    public void startXrefAtPlainObjectReindexes() throws Exception {
        Document document = open(build(false));
        try {
            assertEquals(1, document.getNumberOfPages());
        } finally {
            document.dispose();
        }
        assertFalse(warnings.isEmpty(), "expected the reindex warning");
        Throwable cause = warnings.get(0).getThrown();
        assertNotNull(cause);
        assertFalse(cause instanceof ClassCastException, "cast failure: " + cause);
        assertInstanceOf(ObjectStateException.class, cause);
    }

    @DisplayName("a hybrid file's /XRefStm at a plain object is ignored; the table is used without reindexing")
    @Test
    public void badXrefStmIsIgnored() throws Exception {
        Document document = open(build(true));
        try {
            assertEquals(1, document.getNumberOfPages());
        } finally {
            document.dispose();
        }
        assertTrue(warnings.isEmpty(), "should not reindex: " + warnings.stream()
                .map(r -> r.getMessage() + " " + r.getThrown()).reduce("", String::concat));
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "xref-pointer.pdf");
        return document;
    }

    /**
     * hybrid=false: no xref table, startxref points at the catalog object.
     * hybrid=true: a valid xref table whose trailer /XRefStm points at the catalog object.
     */
    private static byte[] build(boolean hybrid) {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.5\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int catalogOffset = offsets.get(0);
        if (!hybrid) {
            pdf.append("startxref\n").append(catalogOffset).append("\n%%EOF\n");
        } else {
            int xref = pdf.length();
            pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
            for (int offset : offsets) {
                pdf.append(String.format("%010d 00000 n \n", offset));
            }
            pdf.append("trailer\n<< /Root 1 0 R /Size ").append(objects.size() + 1)
                    .append(" /XRefStm ").append(catalogOffset).append(" >>\nstartxref\n").append(xref)
                    .append("\n%%EOF\n");
        }
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
