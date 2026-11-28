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
package org.icepdf.core.internal;

/**
 * Sleeps that don't heed interrupts (GH-662): the viewer interrupts the renders it cancels, and what they wait for
 * is wanted anyway. The thread stays interrupted for its caller to see.
 */
public final class Uninterruptibly {

    private Uninterruptibly() {
    }

    public static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        long until = System.nanoTime() + millis * 1_000_000;
        boolean interrupted = false;
        for (long left = millis; left > 0; left = (until - System.nanoTime() + 999_999) / 1_000_000) {
            try {
                Thread.sleep(left);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
