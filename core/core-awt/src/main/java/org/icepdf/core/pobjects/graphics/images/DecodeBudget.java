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
package org.icepdf.core.pobjects.graphics.images;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caps the heap that image decodes in flight may claim at once, process-wide.  A large scan decodes
 * to its full size whatever the zoom it is drawn at - a 9000 x 9000 page is ~650MB of samples and
 * ARGB raster - and the image pool and every render thread decode concurrently, so a screenful of
 * such pages at a low zoom asks for several of those at the same moment and the heap runs out with
 * most of it in decodes that would each have fitted alone.
 * <p>
 * Each decode estimates its peak (width x height x {@value #BYTES_PER_PIXEL}: the inflated samples
 * plus the ARGB raster) and waits for that much budget; small images ({@value #FREE_MB}MB or less)
 * never wait.  A decode bigger than the whole budget takes all of it, so it runs alone rather than
 * not at all.  The budget defaults to a quarter of the max heap; {@value #BUDGET_PROPERTY} sets it in
 * MB.  A decode never waits longer than {@value #MAX_WAIT_SECONDS}s, so a decode that is somehow
 * waited on by a budget holder can't deadlock.  Decodes nested on one thread (a mask inside its
 * image) take nothing more.
 */
public final class DecodeBudget {

    private static final Logger logger = Logger.getLogger(DecodeBudget.class.getName());

    public static final String BUDGET_PROPERTY = "org.icepdf.core.images.decodeBudget";
    static final int BYTES_PER_PIXEL = 8;
    static final int FREE_MB = 4;
    static final int MAX_WAIT_SECONDS = 30;

    private static final int BUDGET_MB = Math.max(FREE_MB + 1, Integer.getInteger(BUDGET_PROPERTY,
            (int) (Runtime.getRuntime().maxMemory() / 4 >> 20)));
    private static final Semaphore BUDGET = new Semaphore(BUDGET_MB, true);
    private static final ThreadLocal<Boolean> HOLDING = ThreadLocal.withInitial(() -> false);

    /** What a decode holds; closing it gives the budget back. */
    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    private static final Permit NONE = () -> {
    };

    private DecodeBudget() {
    }

    /** The decode's peak estimate in MB, rounded up. */
    static int estimateMb(int width, int height) {
        long bytes = (long) Math.max(0, width) * Math.max(0, height) * BYTES_PER_PIXEL;
        return (int) Math.min(Integer.MAX_VALUE, (bytes + (1 << 20) - 1) >> 20);
    }

    /**
     * Waits for budget for a decode of the given size.
     *
     * @return the permit to close once the decode is done (in a finally or try-with-resources)
     */
    public static Permit acquire(int width, int height) {
        int mb = Math.min(estimateMb(width, height), BUDGET_MB);
        if (mb <= FREE_MB || HOLDING.get()) return NONE;
        try {
            if (!BUDGET.tryAcquire(mb, MAX_WAIT_SECONDS, TimeUnit.SECONDS)) {
                logger.log(Level.FINE, () -> "Image decode budget wait timed out; decoding " + width + "x" + height
                        + " anyway");
                return NONE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NONE;
        }
        HOLDING.set(true);
        return () -> {
            HOLDING.set(false);
            BUDGET.release(mb);
        };
    }

    /** The budget in MB. */
    public static int getBudgetMb() {
        return BUDGET_MB;
    }

    /** MB of the budget free right now; for diagnostics. */
    public static int getAvailableMb() {
        return BUDGET.availablePermits();
    }
}
