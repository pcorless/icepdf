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
 * The memory manageable interface is used by the Memory Manager to free the
 * memory of a PDF object when they are no longer needed.
 *
 * @author Mark Collette
 * @since 2.0
 */
public interface MemoryManageable {

    /**
     * Reduce the amount of memory used by the implementing class.  In most
     * cases the PDF object dispose method should be called.
     */
    public void reduceMemory();

    /**
     * Get the document's library object.
     *
     * @return document's library object.
     */
    public Library getLibrary();
}
