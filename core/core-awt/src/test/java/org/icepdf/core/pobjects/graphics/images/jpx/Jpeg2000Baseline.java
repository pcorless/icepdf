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
package org.icepdf.core.pobjects.graphics.images.jpx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provenance: ORIGINAL.
 * <p>
 * Per-reader expected outcomes: {@code src/test/resources/jpx/baseline/<suite>-<reader>.tsv}, one
 * {@code id<TAB>outcome<TAB>detail} line per check. A test asserts the outcome column only; the detail is
 * there for people. Run with {@code -Djpx.bless=true} to rewrite the file from the current reader.
 * Every run also writes the full report to {@code build/reports/jpx/}.
 */
final class Jpeg2000Baseline {

    private final String suite;
    private final Map<String, String> expected = new TreeMap<>();
    private final Map<String, String> actual = new TreeMap<>();

    Jpeg2000Baseline(String suite) {
        this.suite = suite;
        String file = "baseline/" + fileName();
        try {
            for (String line : Jpeg2000Fixtures.text(file).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t", 3);
                expected.put(f[0], f[1]);
            }
        } catch (IllegalArgumentException missing) {
            // no baseline for this reader yet: every check reports as new
        }
    }

    String fileName() {
        return suite + "-" + Jpeg2000Fixtures.readerLabel() + ".tsv";
    }

    static boolean blessing() {
        return Boolean.getBoolean("jpx.bless");
    }

    /** Null when the baseline has no entry. */
    synchronized String expected(String id) {
        return expected.get(id);
    }

    synchronized void record(String id, String outcome, String detail) {
        actual.put(id, outcome + "\t" + detail.replace('\t', ' ').replace('\n', ' '));
    }

    synchronized void write() {
        StringBuilder sb = new StringBuilder("# GH-613 ").append(suite).append(" baseline for ")
                .append(Jpeg2000Fixtures.readerSpi() == null ? "no reader"
                        : Jpeg2000Fixtures.readerSpi().getClass().getName()
                        + " " + Jpeg2000Fixtures.readerSpi().getVersion())
                .append("\n# id\toutcome\tdetail (not asserted)\n");
        actual.forEach((k, v) -> sb.append(k).append('\t').append(v).append('\n'));
        try {
            Path report = Paths.get("build", "reports", "jpx", fileName());
            Files.createDirectories(report.getParent());
            Files.write(report, sb.toString().getBytes(StandardCharsets.UTF_8));
            if (blessing()) {
                Path base = Paths.get("src", "test", "resources", "jpx", "baseline", fileName());
                Files.createDirectories(base.getParent());
                Files.write(base, sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
