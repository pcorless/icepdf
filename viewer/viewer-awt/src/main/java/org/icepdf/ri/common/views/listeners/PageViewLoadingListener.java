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
package org.icepdf.ri.common.views.listeners;

import org.icepdf.core.events.PageLoadingAdapter;
import org.icepdf.ri.common.views.DocumentViewController;

/**
 * PageViewLoadingListener allows for multiple implementation of a
 * PageViewLoading Listener.
 *
 * @since 5.1.0
 */
public abstract class PageViewLoadingListener extends PageLoadingAdapter {

    /**
     * Sets the ne document view controller set when a view type changes.
     *
     * @param documentViewController currently selected document view controller.
     */
    public abstract void setDocumentViewController(DocumentViewController documentViewController);

}
