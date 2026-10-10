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
package org.icepdf.fx.panels;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.OptionalContent;
import org.icepdf.core.pobjects.OptionalContentGroup;
import org.icepdf.core.pobjects.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row of a document's layer list: an optional content group, which can be switched on and off,
 * or a label heading a collection of them.  Built from the default configuration's {@code /Order}
 * (PDF 32000-1 8.11.4.3): an array after a group holds that group's children, and an array that
 * starts with a text string is a labelled collection.  A document without an {@code /Order} lists
 * its groups flat.
 */
public final class LayerNode {

    private final OptionalContentGroup group;
    private final String label;
    private final List<LayerNode> children = new ArrayList<>();
    // the radio-button groups this group belongs to: switching it on switches the others off.
    private final List<List<OptionalContentGroup>> radioGroups = new ArrayList<>();

    private LayerNode(OptionalContentGroup group, String label) {
        this.group = group;
        this.label = label;
    }

    /** The document's layers, top level first; empty when it has none. */
    public static List<LayerNode> of(Document document) {
        OptionalContent content = document != null ? document.getCatalog().getOptionalContent() : null;
        if (content == null) return Collections.emptyList();
        content.init();
        List<Object> order = content.getOrder();
        List<LayerNode> nodes;
        if (order != null && !order.isEmpty()) {
            nodes = build(order);
        } else {
            nodes = new ArrayList<>();
            Object groups = content.getLibrary().getObject(content.getEntries(), OptionalContent.OCGs_KEY);
            if (groups instanceof List) {
                for (Object ref : (List<?>) groups) {
                    OptionalContentGroup group = ref instanceof Reference ? content.getOCGs((Reference) ref) : null;
                    if (group != null) nodes.add(new LayerNode(group, null));
                }
            }
        }
        List<List<OptionalContentGroup>> radio = radioGroups(content);
        if (!radio.isEmpty()) attachRadioGroups(nodes, radio);
        return nodes;
    }

    private static List<LayerNode> build(List<?> order) {
        List<LayerNode> nodes = new ArrayList<>();
        for (Object item : order) {
            if (item instanceof OptionalContentGroup) {
                nodes.add(new LayerNode((OptionalContentGroup) item, null));
            } else if (item instanceof String) {
                nodes.add(new LayerNode(null, (String) item));
            } else if (item instanceof List) {
                List<?> sub = (List<?>) item;
                if (!sub.isEmpty() && sub.get(0) instanceof String) {
                    LayerNode labelled = new LayerNode(null, (String) sub.get(0));
                    labelled.children.addAll(build(sub.subList(1, sub.size())));
                    nodes.add(labelled);
                } else {
                    List<LayerNode> built = build(sub);
                    LayerNode last = nodes.isEmpty() ? null : nodes.get(nodes.size() - 1);
                    if (last != null && last.group != null) last.children.addAll(built);
                    else nodes.addAll(built);
                }
            }
        }
        return nodes;
    }

    private static List<List<OptionalContentGroup>> radioGroups(OptionalContent content) {
        List<List<OptionalContentGroup>> groups = new ArrayList<>();
        List<Object> rb = content.getRbGroups();
        if (rb == null) return groups;
        for (Object set : rb) {
            if (!(set instanceof List)) continue;
            List<OptionalContentGroup> members = new ArrayList<>();
            for (Object member : (List<?>) set) {
                if (member instanceof OptionalContentGroup) members.add((OptionalContentGroup) member);
            }
            if (members.size() > 1) groups.add(members);
        }
        return groups;
    }

    private static void attachRadioGroups(List<LayerNode> nodes, List<List<OptionalContentGroup>> radio) {
        for (LayerNode node : nodes) {
            if (node.group != null) {
                for (List<OptionalContentGroup> set : radio) {
                    if (set.contains(node.group)) node.radioGroups.add(set);
                }
            }
            attachRadioGroups(node.children, radio);
        }
    }

    /** The optional content group, or null for a label. */
    public OptionalContentGroup group() {
        return group;
    }

    /** The text shown: the group's name, or the label. */
    public String name() {
        if (group != null) return group.getName() != null ? group.getName() : "";
        return label != null ? label : "";
    }

    public List<LayerNode> children() {
        return Collections.unmodifiableList(children);
    }

    public boolean isLabel() {
        return group == null;
    }

    public boolean isVisible() {
        return group != null && group.isVisible();
    }

    /** True when the group is in a radio-button set (only one of the set can be on). */
    public boolean isRadio() {
        return !radioGroups.isEmpty();
    }

    /**
     * Switches the group on or off; switching on a member of a radio-button set switches the rest of
     * the set off.  Labels ignore it.
     */
    public void setVisible(boolean visible) {
        if (group == null) return;
        group.setVisible(visible);
        if (visible) {
            for (List<OptionalContentGroup> set : radioGroups) {
                for (OptionalContentGroup other : set) {
                    if (other != group) other.setVisible(false);
                }
            }
        }
    }

    @Override
    public String toString() {
        return name();
    }
}
