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
package org.icepdf.core.pobjects.graphics.text;

/**
 * An immutable, normalized half-open character-offset range {@code [start, end)} into a
 * {@link TextSequence}.  {@code start} is always {@code <= end}.
 *
 * @see TextSequence
 * @since 7.5
 */
public final class OffsetRange {

    private final int start;
    private final int end;

    private OffsetRange(int start, int end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Creates a normalized range from two offsets in either order.
     *
     * @param a one endpoint
     * @param b the other endpoint
     * @return range with {@code start = min(a,b)}, {@code end = max(a,b)}
     */
    public static OffsetRange of(int a, int b) {
        return a <= b ? new OffsetRange(a, b) : new OffsetRange(b, a);
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int length() {
        return end - start;
    }

    public boolean isEmpty() {
        return end == start;
    }

    public boolean contains(int offset) {
        return offset >= start && offset < end;
    }

    public OffsetRange union(OffsetRange other) {
        return new OffsetRange(Math.min(start, other.start), Math.max(end, other.end));
    }

    /**
     * Returns this range clamped so both endpoints fall within {@code [0, max]}.
     *
     * @param max upper bound (typically the sequence length)
     * @return clamped range
     */
    public OffsetRange clamp(int max) {
        int s = Math.max(0, Math.min(start, max));
        int e = Math.max(0, Math.min(end, max));
        return of(s, e);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OffsetRange)) return false;
        OffsetRange that = (OffsetRange) o;
        return start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return 31 * start + end;
    }

    @Override
    public String toString() {
        return "OffsetRange[" + start + ", " + end + ")";
    }
}
