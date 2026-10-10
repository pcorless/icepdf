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
package org.icepdf.fx.ri.icons;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Icons drawn from SVG path data: one path per id, on a 24x24 grid, stroked and never filled -
 * the conventions of Lucide and Tabler, so their paths can be dropped in.  Each icon is an
 * {@link SVGPath} with the style classes {@code icon} (its square box) and {@code icon-path} (the
 * stroke), so the theme decides the colour ({@code -fx-stroke}); without a theme it's dark grey.
 * <p>
 * The file format is a properties file, {@code id=path data}.
 */
public final class SvgIcons implements IconProvider {

    /** The grid the paths are drawn on. */
    public static final double GRID = 24;

    private static SvgIcons placeholders;
    private final Map<String, String> paths;

    private SvgIcons(Map<String, String> paths) {
        this.paths = Collections.unmodifiableMap(paths);
    }

    /** The viewer's own placeholder set (every built-in action, panel and UI id). */
    public static synchronized SvgIcons placeholders() {
        if (placeholders == null) {
            try (InputStream in = SvgIcons.class.getResourceAsStream("/org/icepdf/fx/ri/icons/placeholder-icons.properties")) {
                placeholders = from(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return placeholders;
    }

    /** Icons from a properties stream of {@code id=SVG path data}. */
    public static SvgIcons from(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        Map<String, String> paths = new LinkedHashMap<>();
        for (String id : properties.stringPropertyNames()) paths.put(id, properties.getProperty(id).trim());
        return new SvgIcons(paths);
    }

    /** Icons from a properties file of {@code id=SVG path data}. */
    public static SvgIcons from(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return from(in);
        }
    }

    /** Icons from a map of id to SVG path data. */
    public static SvgIcons of(Map<String, String> paths) {
        return new SvgIcons(new LinkedHashMap<>(paths));
    }

    /** The ids this set has. */
    public Set<String> ids() {
        return paths.keySet();
    }

    /** The path data for an id, or null. */
    public String path(String id) {
        return paths.get(id);
    }

    @Override
    public Node icon(String id, double size) {
        String data = paths.get(id);
        if (data == null) return null;
        SVGPath path = new SVGPath();
        path.setContent(data);
        path.getStyleClass().add("icon-path");
        // defaults a theme overrides.
        path.setFill(null);
        path.setStroke(Color.web("#2e3436"));
        path.setStrokeWidth(2);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        // scale the 24 grid to the size, about the origin, inside a fixed square.
        Group drawing = new Group(path);
        drawing.getTransforms().add(new Scale(size / GRID, size / GRID, 0, 0));
        drawing.setManaged(false);
        StackPane box = new StackPane(drawing);
        box.getStyleClass().add("icon");
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setUserData(id);
        return box;
    }
}
