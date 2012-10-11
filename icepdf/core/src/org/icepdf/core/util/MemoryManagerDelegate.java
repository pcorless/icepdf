/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.util;

/**
 * Sometimes it might not make sense to have a fullblown MemoryManager taking
 * care of some resources, but instead have a delegate that the MemoryManager
 * can make use of. This is the interface for those delegates to implement.
 *
 * @author Mark Collette
 * @since 2.0
 */
public interface MemoryManagerDelegate {
    /**
     * Since we don't always want to eliminate all caching,
     * when we're low on memory, for performance reasons,
     * this is an indicator to conservatively reduce memory
     */
    public static final int REDUCE_SOMEWHAT = 0;
    /**
     * When we're desperately low on memory, we should aggressively
     * reduce memory usage, or else being too timid will simply waste
     * time
     */
    public static final int REDUCE_AGGRESSIVELY = 1;

    /**
     * Reduce the amount of memory, as managed by this delegate.
     * In most cases the PDF object dispose method should be called.
     *
     * @return true, if any memory was reduced; false, otherwise
     */
    public boolean reduceMemory(int reductionPolicy);

    /**
     * Get the documents library object.
     *
     * @return documents library object.
     */
    public Library getLibrary();
}
