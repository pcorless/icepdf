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
package org.icepdf.fx.ri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The user's own arrangement, within what the product allows ({@link ViewerFeatures}): which tools
 * are pinned to the tool rail and in what order, which are hidden, where the rail and the side
 * (utility) panel sit, and how the panel opens.  Saved in a properties file
 * ({@code layout.properties} in the viewer's settings directory), never {@code java.util.prefs}.
 * <p>
 * Until the user pins something, {@link #getPinned()} is empty and the product's default rail is
 * used ({@link #isRailCustomised()}).
 */
public final class UserLayout {

    private static final Logger logger = Logger.getLogger(UserLayout.class.getName());

    /** Which side of the document. */
    public enum Side { LEFT, RIGHT }

    static final String PINNED = "rail.pinned";
    static final String HIDDEN = "rail.hidden";
    static final String RAIL_SIDE = "rail.side";
    static final String RAIL_AUTO_SHOW = "rail.autoShow";
    static final String PANEL_SIDE = "panel.side";
    static final String PANEL_PINNED = "panel.pinned";
    static final String PANEL_HOVER_OPEN = "panel.hoverOpen";
    static final String PANEL_WIDTH = "panel.width";
    static final String PANEL_LAST = "panel.last";

    private final Path file;
    private List<String> pinned = new ArrayList<>();
    private boolean railCustomised;
    private final Set<String> hidden = new LinkedHashSet<>();
    private Side railSide = Side.LEFT;
    private boolean railAutoShow;
    private Side panelSide = Side.RIGHT;
    private boolean panelPinned;
    private boolean panelHoverOpen = true;
    private double panelWidth = 260;
    private String lastPanel = SidePanel.THUMBNAILS.id();

    private UserLayout(Path file) {
        this.file = file;
    }

    /** A layout read from {@code file} (defaults when it doesn't exist yet), saved back there. */
    public static UserLayout load(Path file) {
        UserLayout layout = new UserLayout(file);
        if (file != null && Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
                layout.read(p);
            } catch (IOException | IllegalArgumentException e) {
                logger.log(Level.WARNING, "Could not read the layout from " + file + "; using defaults", e);
            }
        }
        return layout;
    }

    /** A layout that is never read or saved. */
    public static UserLayout inMemory() {
        return new UserLayout(null);
    }

    public Path getFile() {
        return file;
    }

    // ---- tool rail ----------------------------------------------------------------------------

    /** The pinned tools in order; empty until the user pins something. */
    public List<String> getPinned() {
        return Collections.unmodifiableList(pinned);
    }

    /** Pins these tools to the rail, in this order (replacing the product's default). */
    public void setPinned(List<String> ids) {
        pinned = new ArrayList<>(new LinkedHashSet<>(ids));
        railCustomised = true;
        hidden.removeAll(pinned);
    }

    /** Whether the user has arranged the rail (else the product's default applies). */
    public boolean isRailCustomised() {
        return railCustomised;
    }

    /** Back to the product's default rail; hidden tools come back too. */
    public void resetRail() {
        pinned.clear();
        hidden.clear();
        railCustomised = false;
    }

    /** Tools the user doesn't want, not even in the overflow. */
    public Set<String> getHidden() {
        return Collections.unmodifiableSet(hidden);
    }

    public void setHidden(String id, boolean hide) {
        if (hide) {
            hidden.add(id);
            if (pinned.remove(id)) railCustomised = true;
        } else {
            hidden.remove(id);
        }
    }

    public Side getRailSide() {
        return railSide;
    }

    /** Puts the rail on a side; the side panel moves to the other one. */
    public void setRailSide(Side side) {
        railSide = Objects.requireNonNull(side);
        panelSide = side == Side.LEFT ? Side.RIGHT : Side.LEFT;
    }

    /** Whether the rail hides until the pointer reaches its edge. */
    public boolean isRailAutoShow() {
        return railAutoShow;
    }

    public void setRailAutoShow(boolean autoShow) {
        railAutoShow = autoShow;
    }

    // ---- side (utility) panel ------------------------------------------------------------------

    public Side getPanelSide() {
        return panelSide;
    }

    /** Puts the side panel on a side; the rail moves to the other one. */
    public void setPanelSide(Side side) {
        panelSide = Objects.requireNonNull(side);
        railSide = side == Side.LEFT ? Side.RIGHT : Side.LEFT;
    }

    /** Docked beside the document (true) or collapsed to its icon strip (false). */
    public boolean isPanelPinned() {
        return panelPinned;
    }

    public void setPanelPinned(boolean pinned) {
        panelPinned = pinned;
    }

    /** Whether resting the pointer on the icon strip opens the panel over the page. */
    public boolean isPanelHoverOpen() {
        return panelHoverOpen;
    }

    public void setPanelHoverOpen(boolean hoverOpen) {
        panelHoverOpen = hoverOpen;
    }

    /** The panel's width when open, in pixels. */
    public double getPanelWidth() {
        return panelWidth;
    }

    public void setPanelWidth(double width) {
        if (width > 0 && Double.isFinite(width)) panelWidth = width;
    }

    /** The panel shown last ({@link SidePanel#id()}). */
    public String getLastPanel() {
        return lastPanel;
    }

    public void setLastPanel(String panel) {
        if (panel != null && !panel.isBlank()) lastPanel = panel;
    }

    // ---- persistence ----------------------------------------------------------------------------

    /** Writes the file (atomically); does nothing for an in-memory layout. */
    public void save() {
        if (file == null) return;
        Properties p = write();
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                p.store(out, "ICEpdf JavaFX viewer layout");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save the layout to " + file, e);
        }
    }

    Properties write() {
        Properties p = new Properties();
        if (railCustomised) p.setProperty(PINNED, String.join(",", pinned));
        if (!hidden.isEmpty()) p.setProperty(HIDDEN, String.join(",", hidden));
        p.setProperty(RAIL_SIDE, railSide.name());
        p.setProperty(RAIL_AUTO_SHOW, String.valueOf(railAutoShow));
        p.setProperty(PANEL_SIDE, panelSide.name());
        p.setProperty(PANEL_PINNED, String.valueOf(panelPinned));
        p.setProperty(PANEL_HOVER_OPEN, String.valueOf(panelHoverOpen));
        p.setProperty(PANEL_WIDTH, String.valueOf(panelWidth));
        p.setProperty(PANEL_LAST, lastPanel);
        return p;
    }

    void read(Properties p) {
        String pinnedValue = p.getProperty(PINNED);
        if (pinnedValue != null) {
            pinned = list(pinnedValue);
            railCustomised = true;
        }
        hidden.addAll(list(p.getProperty(HIDDEN)));
        railSide = side(p.getProperty(RAIL_SIDE), railSide);
        panelSide = side(p.getProperty(PANEL_SIDE), railSide == Side.LEFT ? Side.RIGHT : Side.LEFT);
        if (panelSide == railSide) panelSide = railSide == Side.LEFT ? Side.RIGHT : Side.LEFT;
        railAutoShow = Boolean.parseBoolean(p.getProperty(RAIL_AUTO_SHOW, String.valueOf(railAutoShow)));
        panelPinned = Boolean.parseBoolean(p.getProperty(PANEL_PINNED, String.valueOf(panelPinned)));
        panelHoverOpen = Boolean.parseBoolean(p.getProperty(PANEL_HOVER_OPEN, String.valueOf(panelHoverOpen)));
        try {
            setPanelWidth(Double.parseDouble(p.getProperty(PANEL_WIDTH, String.valueOf(panelWidth))));
        } catch (NumberFormatException ignored) {
            // keep the default
        }
        setLastPanel(p.getProperty(PANEL_LAST));
    }

    private static List<String> list(String value) {
        List<String> items = new ArrayList<>();
        if (value == null) return items;
        for (String part : value.split(",")) {
            if (!part.isBlank()) items.add(part.trim());
        }
        return items;
    }

    private static Side side(String value, Side fallback) {
        try {
            return value != null ? Side.valueOf(value.trim().toUpperCase(Locale.ROOT)) : fallback;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
