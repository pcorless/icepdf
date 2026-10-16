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
package org.icepdf.fx.render;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;

/**
 * A render {@link RenderService.Owner} that is in the foreground while its node is in a scene whose
 * window is showing and focused.  A view in a background tab (out of the scene) or a window behind
 * another is in the background.  When that changes, the owner's queued work is
 * {@link RenderService#reprioritise re-ranked} and {@code onForeground} runs on gaining it.
 * <p>
 * FX thread only, apart from {@link #isForeground()}.
 */
public final class NodeOwner implements RenderService.Owner {

    private final RenderService service;
    private final Runnable onForeground;
    private final ObservableValue<Boolean> showing;
    private final ChangeListener<Boolean> listener = (obs, was, now) -> update(now);
    private volatile boolean foreground;

    /**
     * @param node         the view (or the view a panel serves)
     * @param onForeground run when the node comes to the foreground; may be null
     */
    public NodeOwner(Node node, RenderService service, Runnable onForeground) {
        this.service = service;
        this.onForeground = onForeground;
        showing = node.sceneProperty()
                .flatMap(Scene::windowProperty)
                .flatMap(window -> window.focusedProperty()
                        .flatMap(focused -> window.showingProperty().map(shown -> focused && shown)))
                .orElse(false);
        showing.addListener(listener);
        foreground = showing.getValue();
    }

    @Override
    public boolean isForeground() {
        return foreground;
    }

    /** Stops following the node; the owner stays in the background from now on. */
    public void dispose() {
        showing.removeListener(listener);
        update(false);
    }

    private void update(boolean now) {
        if (now == foreground) return;
        foreground = now;
        service.reprioritise(this);
        if (now && onForeground != null) onForeground.run();
    }
}
