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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Writes everything a QA run logs at WARNING or above, plus anything printed to {@code System.err}, to one file
 * per run under {@code <results>/logs/}, with every entry tagged with the document and page being captured.
 * <p>
 * Capture threads tag themselves with {@link #setContext(String)}.  ICEpdf also does work on its own pool threads
 * (image decoding, for one) that don't know which document they serve; entries from those carry the thread name
 * and the documents in flight at that moment, which with a handful of concurrent documents narrows it down.
 * <p>
 * At the end of the run a summary groups identical problems - same level, logger, message and top stack frames -
 * with a count and example documents, appended to the log and written to a separate {@code -summary.txt}.
 * <p>
 * {@code -Dqa.log.level=FINE} (any {@link Level} name) changes the threshold for logged records.
 */
public final class CaptureLog {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LINE_STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int SUMMARY_FRAMES = 3;
    private static final int SUMMARY_EXAMPLES = 5;

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private static CaptureLog current;

    private final Path logFile;
    private final Writer out;
    private final Handler handler;
    private final PrintStream originalErr;
    private final Map<String, Problem> problems = new LinkedHashMap<>();
    private int stderrLines;

    private CaptureLog(Path logFile, Level level) throws IOException {
        this.logFile = logFile;
        this.out = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
        this.handler = new RecordHandler();
        this.handler.setLevel(level);
        this.originalErr = System.err;
    }

    /**
     * Starts a run log in {@code <resultsDirectory>/logs}, replacing any log still open.
     *
     * @param resultsDirectory QA results directory
     * @param title            first line of the log, e.g. the project and capture sets
     * @return the started log, or null if the file couldn't be created (the run continues without one)
     */
    public static synchronized CaptureLog start(Path resultsDirectory, String title) {
        if (current != null) {
            current.finish();
        }
        try {
            Path directory = resultsDirectory.resolve("logs");
            Files.createDirectories(directory);
            Path file = directory.resolve("qa-" + LocalDateTime.now().format(FILE_STAMP) + ".log");
            Level level = Level.WARNING;
            try {
                level = Level.parse(System.getProperty("qa.log.level", "WARNING"));
            } catch (IllegalArgumentException e) {
                // keep WARNING
            }
            CaptureLog log = new CaptureLog(file, level);
            log.write(title + "\n" + "Started " + LocalDateTime.now() + ", logging " + level + " and above"
                    + " and everything on System.err\n\n");
            Logger.getLogger("").addHandler(log.handler);
            System.setErr(new PrintStream(new ErrTee(log, log.originalErr), true));
            current = log;
            System.out.println("QA log: " + file);
            return log;
        } catch (IOException e) {
            System.out.println("Could not start the QA log: " + e.getMessage());
            return null;
        }
    }

    /**
     * Ends the run: writes the summary, detaches from logging and {@code System.err}, closes the file.
     */
    public synchronized void finish() {
        if (current != this) {
            return;
        }
        Logger.getLogger("").removeHandler(handler);
        System.setErr(originalErr);
        current = null;
        String summary = summary();
        write("\n" + summary);
        try {
            out.close();
        } catch (IOException e) {
            originalErr.println("Could not close the QA log: " + e.getMessage());
        }
        Path summaryFile = logFile.resolveSibling(logFile.getFileName().toString().replace(".log", "-summary.txt"));
        try {
            Files.write(summaryFile, summary.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            originalErr.println("Could not write the QA log summary: " + e.getMessage());
        }
        System.out.println("QA log: " + problems.size() + " distinct problem(s), "
                + problems.values().stream().mapToInt(p -> p.count).sum() + " logged, "
                + stderrLines + " line(s) of System.err; summary " + summaryFile);
    }

    /** @return the log file of this run */
    public Path getLogFile() {
        return logFile;
    }

    /**
     * Tags the calling thread's log entries, e.g. {@code "A: out.pdf p3"}; the document part (before the first
     * " p") is also listed as in flight for entries from untagged threads.
     *
     * @param context what the thread is working on
     */
    public static void setContext(String context) {
        CONTEXT.set(context);
        IN_FLIGHT.add(context);
    }

    /** Clears the calling thread's tag. */
    public static void clearContext() {
        String context = CONTEXT.get();
        if (context != null) {
            IN_FLIGHT.remove(context);
        }
        CONTEXT.remove();
    }

    private static String contextOfCurrentThread() {
        String context = CONTEXT.get();
        if (context != null) {
            return context;
        }
        List<String> inFlight = new ArrayList<>(IN_FLIGHT);
        inFlight.sort(Comparator.naturalOrder());
        return Thread.currentThread().getName() + (inFlight.isEmpty() ? "" : "; in flight: " + String.join(", ", inFlight));
    }

    private synchronized void write(String text) {
        try {
            out.write(text);
            out.flush();
        } catch (IOException e) {
            originalErr.println("Could not write to the QA log: " + e.getMessage());
        }
    }

    private synchronized void record(LogRecord record, String context) {
        StringBuilder entry = new StringBuilder();
        entry.append(LocalDateTime.now().format(LINE_STAMP)).append(" [").append(context).append("] ")
                .append(record.getLevel()).append(' ').append(record.getLoggerName()).append(": ")
                .append(message(record)).append('\n');
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            StringWriter trace = new StringWriter();
            thrown.printStackTrace(new PrintWriter(trace));
            entry.append(trace);
        }
        write(entry.toString());
        String key = signature(record);
        problems.computeIfAbsent(key, k -> new Problem(record.getLevel(), record.getLoggerName(),
                message(record), thrown)).add(context);
    }

    private synchronized void stderrLine(String line, String context) {
        stderrLines++;
        write(LocalDateTime.now().format(LINE_STAMP) + " [" + context + "] stderr: " + line + "\n");
    }

    private static String message(LogRecord record) {
        String message = record.getMessage();
        Object[] parameters = record.getParameters();
        if (message != null && parameters != null && parameters.length > 0) {
            try {
                message = java.text.MessageFormat.format(message, parameters);
            } catch (IllegalArgumentException e) {
                // leave the pattern as it is
            }
        }
        return message;
    }

    /**
     * Groups records that are the same problem: level, logger, the message with its numbers and the exception
     * message stripped of document-specific detail, and the top stack frames.
     */
    static String signature(LogRecord record) {
        StringBuilder key = new StringBuilder();
        key.append(record.getLevel()).append('|').append(record.getLoggerName()).append('|')
                .append(normalise(message(record)));
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            key.append('|').append(thrown.getClass().getName()).append(':').append(normalise(thrown.getMessage()));
            StackTraceElement[] frames = thrown.getStackTrace();
            for (int i = 0; i < Math.min(SUMMARY_FRAMES, frames.length); i++) {
                key.append('|').append(frames[i].getClassName()).append('.').append(frames[i].getMethodName());
            }
        }
        return key.toString();
    }

    /**
     * Strips the document-specific parts of a message so the same problem in different files groups together:
     * dictionary dumps, object references, quoted or bracketed values, then any remaining numbers.
     */
    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String normalised = text;
        // nested {...} dumps: strip innermost first until none remain
        String previous;
        do {
            previous = normalised;
            normalised = normalised.replaceAll("\\{[^{}]*\\}", "{…}");
        } while (!normalised.equals(previous));
        normalised = normalised.replaceAll("\\b\\d+ \\d+ R\\b", "n n R")
                .replaceAll("\"[^\"]*\"", "\"…\"")
                .replaceAll("\\[[^\\[\\]]*\\]", "[…]")
                .replaceAll("\\d+", "#");
        return normalised;
    }

    String summary() {
        StringBuilder summary = new StringBuilder("==== Summary: ").append(problems.size())
                .append(" distinct problem(s), ").append(stderrLines).append(" line(s) of System.err ====\n");
        List<Problem> sorted = new ArrayList<>(problems.values());
        // problems thrown from ICEpdf's own code first - those are the likely bugs - then by count
        sorted.sort(Comparator.comparing((Problem p) -> !p.fromIcepdfCode()).thenComparing(p -> -p.count));
        List<Problem> fontBox = new ArrayList<>();
        boolean headed = false;
        for (Problem problem : sorted) {
            if (problem.logger != null && problem.logger.startsWith("org.apache.fontbox")) {
                fontBox.add(problem);
                continue;
            }
            if (!headed && problem.fromIcepdfCode()) {
                summary.append("\n-- Exceptions thrown from ICEpdf code --\n");
                headed = true;
            } else if (headed && !problem.fromIcepdfCode()) {
                summary.append("\n-- Everything else --\n");
                headed = false;
            }
            summary.append('\n').append(problem.count).append(" x ").append(problem.level).append(' ')
                    .append(problem.logger).append(": ").append(problem.message).append('\n');
            if (problem.thrown != null) {
                summary.append("    ").append(problem.thrown.getClass().getName());
                if (problem.thrown.getMessage() != null) {
                    summary.append(": ").append(problem.thrown.getMessage());
                }
                summary.append('\n');
                StackTraceElement[] frames = problem.thrown.getStackTrace();
                for (int i = 0; i < Math.min(SUMMARY_FRAMES, frames.length); i++) {
                    summary.append("        at ").append(frames[i]).append('\n');
                }
            }
            summary.append("    e.g. ").append(String.join(" | ", problem.examples)).append('\n');
        }
        if (!fontBox.isEmpty()) {
            Map<String, int[]> byLogger = new LinkedHashMap<>();
            for (Problem problem : fontBox) {
                byLogger.computeIfAbsent(problem.logger, k -> new int[2]);
                byLogger.get(problem.logger)[0] += problem.count;
                byLogger.get(problem.logger)[1]++;
            }
            summary.append("\n-- FontBox (").append(fontBox.stream().mapToInt(p -> p.count).sum())
                    .append(" records, by logger) --\n");
            byLogger.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                    .forEach(e -> summary.append(e.getValue()[0]).append(" x ").append(e.getKey())
                            .append(" (").append(e.getValue()[1]).append(" distinct)\n"));
        }
        return summary.toString();
    }

    private static final class Problem {
        final Level level;
        final String logger;
        final String message;
        final Throwable thrown;
        final Set<String> examples = new LinkedHashSet<>();
        int count;

        Problem(Level level, String logger, String message, Throwable thrown) {
            this.level = level;
            this.logger = logger;
            this.message = message;
            this.thrown = thrown;
        }

        /**
         * True when the first non-JDK frame of the stack trace is ICEpdf code: ICEpdf threw, or passed bad
         * values to the JDK (an out-of-range {@code new Color(...)}, say), rather than a library such as FontBox.
         */
        boolean fromIcepdfCode() {
            if (thrown == null) {
                return false;
            }
            for (StackTraceElement frame : thrown.getStackTrace()) {
                String className = frame.getClassName();
                if (className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.")
                        || className.startsWith("jdk.")) {
                    continue;
                }
                return className.startsWith("org.icepdf.");
            }
            return false;
        }

        void add(String context) {
            count++;
            if (examples.size() < SUMMARY_EXAMPLES) {
                examples.add(context);
            }
        }
    }

    private final class RecordHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                record(record, contextOfCurrentThread());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    /**
     * Passes {@code System.err} through to the console and copies each complete line, tagged, into the log.  Lines
     * are assembled per thread so concurrent writers don't interleave mid-line.
     */
    private static final class ErrTee extends OutputStream {
        private final CaptureLog log;
        private final PrintStream console;
        private final ThreadLocal<ByteArrayOutputStream> line = ThreadLocal.withInitial(ByteArrayOutputStream::new);

        ErrTee(CaptureLog log, PrintStream console) {
            this.log = log;
            this.console = console;
        }

        @Override
        public void write(int b) {
            console.write(b);
            if (b == '\n') {
                emit();
            } else if (b != '\r') {
                line.get().write(b);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            console.write(bytes, offset, length);
            for (int i = offset; i < offset + length; i++) {
                if (bytes[i] == '\n') {
                    emit();
                } else if (bytes[i] != '\r') {
                    line.get().write(bytes[i]);
                }
            }
        }

        @Override
        public void flush() {
            console.flush();
        }

        private void emit() {
            ByteArrayOutputStream buffer = line.get();
            log.stderrLine(new String(buffer.toByteArray(), StandardCharsets.UTF_8), contextOfCurrentThread());
            buffer.reset();
        }
    }
}
