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

import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.panels.AnnotationSummary;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The annotation board as text to hand on: Markdown to read, or CSV for a spreadsheet.  What is
 * written is what the board shows - its columns, filter and order.
 */
public final class BoardExport {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private BoardExport() {
    }

    /** A column's name as the board shows it. */
    public static String columnName(BoardModel.Column column) {
        return switch (column.kind()) {
            case LABEL -> column.label().name().isEmpty()
                    ? String.format("#%06X", column.label().color().getRGB() & 0xFFFFFF) : column.label().name();
            case OTHER -> Messages.get("annotationBoard.other");
            case NONE -> Messages.get("annotationBoard.noColour");
            case ALL -> Messages.get("annotationBoard.all");
            case GROUP -> column.title();
        };
    }

    /**
     * A Markdown summary: a heading per column, a bullet per comment with its page, author and
     * date, the text it marks as a quote, its own text, and how many replies it has.
     */
    public static String markdown(String title, List<BoardModel.Column> columns) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(inline(Messages.format("annotationBoard.title", title))).append("\n\n");
        for (BoardModel.Column column : columns) {
            out.append("## ").append(inline(columnName(column))).append(" (").append(column.cards().size()).append(")\n\n");
            for (BoardModel.Card card : column.cards()) {
                AnnotationSummary.Entry entry = card.entry();
                out.append("- **").append(inline(entry.type())).append("**, ")
                        .append(inline(Messages.format("annotationBoard.page", entry.pageIndex() + 1)));
                if (!entry.author().isEmpty()) out.append(" — ").append(inline(entry.author()));
                if (entry.date() != null) out.append(", ").append(entry.date().format(ISO));
                if (AnnotationSummary.privacyEnabled() && entry.annotation().getFlagPrivateContents()) {
                    out.append(" (").append(inline(Messages.get("commentsPanel.privateBadge"))).append(')');
                }
                out.append('\n');
                if (!card.quote().isBlank()) out.append("  > ").append(inline(card.quote())).append('\n');
                String contents = entry.contents().strip();
                if (!contents.isEmpty() && !flat(contents).equals(flat(card.quote()))) {
                    for (String line : contents.split("\\R")) out.append("  ").append(inline(line)).append('\n');
                }
                if (card.replies() > 0) {
                    out.append("  _").append(Messages.format("annotationBoard.replies", card.replies())).append("_\n");
                }
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * CSV, one row per comment: column, page, type, author, date (ISO), private, quote, text,
     * replies.  RFC 4180 quoting; begins with a byte order mark so spreadsheets read it as UTF-8.
     */
    public static String csv(List<BoardModel.Column> columns) {
        StringBuilder out = new StringBuilder("﻿");
        row(out, Messages.get("annotationBoard.csv.column"), Messages.get("annotationBoard.csv.page"),
                Messages.get("annotationBoard.csv.type"), Messages.get("annotationBoard.csv.author"),
                Messages.get("annotationBoard.csv.date"), Messages.get("annotationBoard.csv.private"),
                Messages.get("annotationBoard.csv.quote"), Messages.get("annotationBoard.csv.text"),
                Messages.get("annotationBoard.csv.replies"));
        for (BoardModel.Column column : columns) {
            String name = columnName(column);
            for (BoardModel.Card card : column.cards()) {
                AnnotationSummary.Entry entry = card.entry();
                row(out, name, Integer.toString(entry.pageIndex() + 1), entry.type(), entry.author(),
                        entry.date() != null ? entry.date().format(ISO) : "",
                        Boolean.toString(entry.annotation().getFlagPrivateContents()), card.quote(), entry.contents(),
                        Integer.toString(card.replies()));
            }
        }
        return out.toString();
    }

    private static void row(StringBuilder out, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) out.append(',');
            String cell = cells[i] == null ? "" : cells[i];
            if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
                out.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                out.append(cell);
            }
        }
        out.append("\r\n");
    }

    /** One line of text, with the characters Markdown would read as markup escaped. */
    private static String inline(String text) {
        return flat(text).replaceAll("([\\\\`*_\\[\\]<>#|])", "\\\\$1");
    }

    private static String flat(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
