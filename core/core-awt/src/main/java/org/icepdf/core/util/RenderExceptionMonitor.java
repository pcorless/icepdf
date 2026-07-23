/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diagnostic tripwire for the content parse/paint "swallow points" -- the
 * {@code catch (Exception e) { log; continue }} blocks that keep the engine
 * rendering whatever content it can when an individual operator/stream fails.
 * <p>
 * Those swallow points are load-bearing: rendering available content ALWAYS
 * wins over surfacing an error, so their catch-and-continue behaviour is never
 * changed.  The problem is only that a concurrency bug (a shared object mutated
 * by two render threads at once) throws INTO one of these catches and is then
 * silently absorbed, so the user just sees "missing content" with no signal.
 * <p>
 * This monitor lets a diagnostic run OBSERVE those catches without changing
 * them: each swallow point calls {@link #record(String, Throwable)}, which is a
 * no-op (one volatile-free boolean read) unless the system property
 * {@code org.icepdf.core.debug.renderExceptions=true} is set.  When enabled it
 * de-duplicates by <em>signature</em> (site + exception type + first in-package
 * stack frame) and counts occurrences, so a concurrent corpus sweep can diff
 * the signatures it sees against a serial baseline: anything concurrent-only is
 * a candidate race.  Off by default and safe to leave in the tree.
 *
 * @see "THREADING-RACE-AUDIT-PLAN.md"
 */
public final class RenderExceptionMonitor {

    private static final boolean ENABLED =
            Boolean.getBoolean("org.icepdf.core.debug.renderExceptions");

    // signature -> event (count + first captured detail).  Concurrent: many
    // render threads record at once.
    private static final Map<String, Event> EVENTS = new ConcurrentHashMap<>();

    private RenderExceptionMonitor() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Record a swallowed throwable.  No-op unless enabled.  Never throws.
     *
     * @param site short stable label of the swallow point (e.g. "ContentParser.parse")
     * @param t    the throwable that was (and still will be) swallowed
     */
    public static void record(String site, Throwable t) {
        if (!ENABLED || t == null) {
            return;
        }
        try {
            String firstAppFrame = firstAppFrame(t);
            String sig = site + " | " + t.getClass().getName() + " | " + firstAppFrame;
            Event e = EVENTS.computeIfAbsent(sig, k -> new Event(site, t, firstAppFrame));
            e.count.incrementAndGet();
        } catch (Throwable ignore) {
            // diagnostics must never perturb rendering
        }
    }

    private static String firstAppFrame(Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        if (frames != null) {
            for (StackTraceElement f : frames) {
                if (f.getClassName().startsWith("org.icepdf.")) {
                    return f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber();
                }
            }
            if (frames.length > 0) {
                StackTraceElement f = frames[0];
                return f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber();
            }
        }
        return "(no-frame)";
    }

    /**
     * Distinct signatures currently recorded.
     */
    public static java.util.Set<String> signatures() {
        return new java.util.TreeSet<>(EVENTS.keySet());
    }

    /**
     * Clear all recorded events (call between phases of a sweep).
     */
    public static void clear() {
        EVENTS.clear();
    }

    /**
     * Human-readable report, one line per signature, sorted by count desc.
     */
    public static String report() {
        StringBuilder sb = new StringBuilder();
        EVENTS.values().stream()
                .sorted((a, b) -> Long.compare(b.count.get(), a.count.get()))
                .forEach(e -> sb.append(String.format("%6d  %s%n    msg: %s%n",
                        e.count.get(), e.signature(), e.message)));
        return sb.toString();
    }

    /**
     * One de-duplicated swallow signature.
     */
    public static final class Event {
        final String site;
        final String exceptionClass;
        final String message;
        final String firstAppFrame;
        final AtomicLong count = new AtomicLong();

        Event(String site, Throwable t, String firstAppFrame) {
            this.site = site;
            this.exceptionClass = t.getClass().getName();
            this.message = String.valueOf(t.getMessage());
            this.firstAppFrame = firstAppFrame;
        }

        String signature() {
            return site + " | " + exceptionClass + " | " + firstAppFrame;
        }
    }
}
