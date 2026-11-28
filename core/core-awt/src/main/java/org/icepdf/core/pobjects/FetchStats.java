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
package org.icepdf.core.pobjects;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * What fetching a remote document has cost so far, for a network panel or a log (GH-662): totals, the most recent
 * requests (those still out included), and which parts of the file have arrived. An immutable snapshot; ask
 * {@link Document#getFetchStats()} again for a newer one. Taking one is cheap: poll it a few times a second.
 *
 * @since 8.1
 */
public final class FetchStats {

    /**
     * What a request was made for.
     */
    public enum Initiator {
        /** Opening the document: its start, and its end with the cross-reference. */
        OPEN,
        /** An object read: its dictionary, up to {@code endobj} or its stream. */
        OBJECT,
        /** A stream or another range of known length, and what reads ahead of it. */
        STREAM,
        /** A page's dictionary fetched ahead ({@link PageTree#prefetchPages()}). */
        PREFETCH,
        /** Everything still missing: saving, signature digests, rebuilding a damaged file's index. */
        WHOLE_FILE
    }

    /**
     * One range request.
     */
    public static final class Request {
        private final long id;
        private final long startMillis;
        private final long from;
        private final int length;
        private final Initiator initiator;
        private final String thread;
        private final long queuedMillis;
        private final long durationMillis;
        private final int status;
        private final int retries;
        private final long retryWaitMillis;
        private final String error;

        public Request(long id, long startMillis, long from, int length, Initiator initiator, String thread,
                       long queuedMillis, long durationMillis, int status, int retries, long retryWaitMillis,
                       String error) {
            this.id = id;
            this.startMillis = startMillis;
            this.from = from;
            this.length = length;
            this.initiator = initiator;
            this.thread = thread;
            this.queuedMillis = queuedMillis;
            this.durationMillis = durationMillis;
            this.status = status;
            this.retries = retries;
            this.retryWaitMillis = retryWaitMillis;
            this.error = error;
        }

        /** @return the request's number, from 1 in the order made */
        public long getId() {
            return id;
        }

        /** @return when it was made, in milliseconds after the document began opening */
        public long getStartMillis() {
            return startMillis;
        }

        /** @return the first byte asked for */
        public long getFrom() {
            return from;
        }

        /** @return the bytes asked for */
        public int getLength() {
            return length;
        }

        public Initiator getInitiator() {
            return initiator;
        }

        /** @return the name of the thread that made it */
        public String getThread() {
            return thread;
        }

        /** @return the time it waited for one of the document's request slots before it was sent */
        public long getQueuedMillis() {
            return queuedMillis;
        }

        /** @return the time from sending to the last byte, retries included; -1 while it's still out */
        public long getDurationMillis() {
            return durationMillis;
        }

        /** @return true while the answer hasn't come */
        public boolean isPending() {
            return durationMillis < 0;
        }

        /** @return the HTTP status of the last answer; 0 while pending, or when no answer came at all */
        public int getStatus() {
            return status;
        }

        /** @return the times it was asked again after a busy answer (429 or 503) */
        public int getRetries() {
            return retries;
        }

        /** @return the time spent waiting before those retries */
        public long getRetryWaitMillis() {
            return retryWaitMillis;
        }

        /** @return why it failed, or null if it didn't (or hasn't yet) */
        public String getError() {
            return error;
        }

        public boolean isFailed() {
            return error != null;
        }
    }

    private final long documentLength;
    private final long bytesFetched;
    private final long requestCount;
    private final int inFlight;
    private final int queued;
    private final int maxConcurrent;
    private final long retries;
    private final long failures;
    private final long elapsedMillis;
    private final boolean complete;
    private final List<Request> recent;
    private final BitSet present;
    private final int blockSize;

    public FetchStats(long documentLength, long bytesFetched, long requestCount, int inFlight, int queued,
                      int maxConcurrent, long retries, long failures, long elapsedMillis, boolean complete,
                      List<Request> recent, BitSet present, int blockSize) {
        this.documentLength = documentLength;
        this.bytesFetched = bytesFetched;
        this.requestCount = requestCount;
        this.inFlight = inFlight;
        this.queued = queued;
        this.maxConcurrent = maxConcurrent;
        this.retries = retries;
        this.failures = failures;
        this.elapsedMillis = elapsedMillis;
        this.complete = complete;
        this.recent = Collections.unmodifiableList(recent);
        this.present = (BitSet) present.clone();
        this.blockSize = blockSize;
    }

    /** @return the document's length in bytes */
    public long getDocumentLength() {
        return documentLength;
    }

    /** @return the bytes the server has sent, a little more than the document's share of them that's here */
    public long getBytesFetched() {
        return bytesFetched;
    }

    /** @return the document's bytes that are here */
    public long getBytesPresent() {
        if (complete) {
            return documentLength;
        }
        return Math.min(documentLength, (long) present.cardinality() * blockSize);
    }

    /** @return the requests made so far, all of them (not only the {@link #getRecentRequests() recent} ones) */
    public long getRequestCount() {
        return requestCount;
    }

    /** @return requests sent and not yet answered */
    public int getInFlight() {
        return inFlight;
    }

    /** @return requests waiting for one of the document's slots */
    public int getQueued() {
        return queued;
    }

    /** @return the requests the document may have out at once */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    /** @return retries after busy answers, over every request */
    public long getRetries() {
        return retries;
    }

    /** @return requests that failed */
    public long getFailures() {
        return failures;
    }

    /** @return the time since the document began opening */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /** @return true once every byte of the document is here */
    public boolean isComplete() {
        return complete;
    }

    /** @return the latest requests, oldest first, those still out included; a bounded log */
    public List<Request> getRecentRequests() {
        return recent;
    }

    /**
     * @param cells how many parts to divide the file into
     * @return for each part, the share of its bytes that's here, 0 to 1
     */
    public float[] getCoverage(int cells) {
        float[] coverage = new float[Math.max(0, cells)];
        if (cells <= 0 || documentLength == 0) {
            return coverage;
        }
        if (complete) {
            Arrays.fill(coverage, 1f);
            return coverage;
        }
        long blocks = (documentLength + blockSize - 1) / blockSize;
        for (int cell = 0; cell < cells; cell++) {
            int first = (int) (blocks * cell / cells);
            int last = (int) Math.max(first + 1, blocks * (cell + 1) / cells);
            int here = present.get(first, last).cardinality();
            coverage[cell] = here / (float) (last - first);
        }
        return coverage;
    }
}
