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
package org.icepdf.fx.print;

import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;

/**
 * The print choices worth remembering between prints - printer, paper, sizing, orientation, sides,
 * annotations - as plain names, so an application can keep them in its settings.  Pages and copies
 * are left out: they belong to one print.  {@link PdfPrintDialog#setDefaults} starts from them; a
 * printer, paper or sides value the chosen printer doesn't offer is ignored.
 *
 * @param printer     printer name, or null for the system default
 * @param paper       paper name as {@link MediaSizeName#toString()} gives it ("iso-a4"), or null
 * @param scaling     null for {@link PrintSettings.Scaling#FIT}
 * @param orientation null for {@link PrintSettings.Orientation#AUTO}
 * @param sides       sides as {@link Sides#toString()} gives it ("two-sided-long-edge"), or null
 * @param annotations print annotations and form fields
 */
public record PrintDefaults(String printer, String paper, PrintSettings.Scaling scaling,
                            PrintSettings.Orientation orientation, String sides, boolean annotations) {

    /** The rememberable part of settings a print was made with. */
    public static PrintDefaults of(PrintSettings settings) {
        return new PrintDefaults(settings.getPrinter() != null ? settings.getPrinter().getName() : null,
                settings.getPaper() != null ? settings.getPaper().toString() : null,
                settings.getScaling(), settings.getOrientation(),
                settings.getSides() != null ? settings.getSides().toString() : null,
                settings.isAnnotations());
    }
}
