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
package org.icepdf.fx.ri.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The actions a viewer knows, by id, in registration order.  {@link StandardActions#registry()}
 * gives every built-in one; a product adds its own with {@link #register} and replaces a built-in by
 * registering another action under the same id with {@link #replace}.
 */
public final class ActionRegistry {

    private final Map<String, ViewerAction> actions = new LinkedHashMap<>();

    /** Adds an action.  @throws IllegalArgumentException if its id is taken (use {@link #replace}) */
    public ActionRegistry register(ViewerAction action) {
        if (actions.putIfAbsent(action.id(), action) != null) {
            throw new IllegalArgumentException("An action is already registered as " + action.id());
        }
        return this;
    }

    /** Puts an action in place of the one with the same id (or adds it). */
    public ActionRegistry replace(ViewerAction action) {
        actions.put(action.id(), action);
        return this;
    }

    /** Removes an action; its id is then left out wherever it's listed. */
    public ActionRegistry remove(String id) {
        actions.remove(id);
        return this;
    }

    public Optional<ViewerAction> find(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    /** @throws IllegalArgumentException for an unknown id */
    public ViewerAction get(String id) {
        ViewerAction action = actions.get(id);
        if (action == null) throw new IllegalArgumentException("No action " + id);
        return action;
    }

    public boolean contains(String id) {
        return actions.containsKey(id);
    }

    /** Every action, in registration order. */
    public List<ViewerAction> all() {
        return Collections.unmodifiableList(new ArrayList<>(actions.values()));
    }

    /** The ids that start with a prefix ("annotation." for the annotation tools), in order. */
    public List<String> idsStartingWith(String prefix) {
        List<String> ids = new ArrayList<>();
        for (String id : actions.keySet()) {
            if (id.startsWith(prefix)) ids.add(id);
        }
        return ids;
    }
}
