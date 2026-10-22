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
package org.icepdf.fx.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * The text the JavaFX viewer shows, from the bundle {@code org/icepdf/fx/i18n/messages.properties}
 * and its translations ({@code messages_fr.properties}...), in the default locale.
 * <p>
 * Keys are the area or class a text belongs to, then what it is ({@code searchPanel.matchCase});
 * an action's text is keyed by its id ({@code document.print}, {@code document.print.tooltip}).
 * A value with arguments is a {@link MessageFormat} pattern ({@code {0}}, and {@code ''} for an
 * apostrophe); a value without is plain text.
 */
public final class Messages {

    private static final Logger logger = Logger.getLogger(Messages.class.getName());
    private static final String BUNDLE = "org.icepdf.fx.i18n.messages";

    private Messages() {
    }

    /** The bundle for the default locale. */
    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle(BUNDLE, Locale.getDefault());
    }

    /** The text for a key; the key itself when the bundle lacks it. */
    public static String get(String key) {
        try {
            return bundle().getString(key);
        } catch (MissingResourceException e) {
            logger.warning("No message " + key);
            return key;
        }
    }

    /** The text for a key, or {@code fallback} when the bundle lacks it (for optional keys). */
    public static String get(String key, String fallback) {
        ResourceBundle bundle = bundle();
        return bundle.containsKey(key) ? bundle.getString(key) : fallback;
    }

    /**
     * Why something failed, for a message: the exception's own message, or its kind when it has
     * none (an NPE, many printer errors) - never "null".  A wrapper that only repeats its cause
     * gives the cause's reason; a file error gives what went wrong rather than the file's path,
     * which the message around it names already.
     */
    public static String reason(Throwable failure) {
        if (failure == null) return "";
        Throwable cause = failure.getCause();
        String message = failure.getLocalizedMessage();
        if (cause != null && cause != failure && (message == null || message.isBlank() || message.equals(cause.toString()))) {
            return reason(cause);
        }
        String file = fileReason(failure);
        if (file != null) return file;
        if (message != null && !message.isBlank()) return message;
        return failure.getClass().getSimpleName();
    }

    private static String fileReason(Throwable failure) {
        if (failure instanceof java.nio.file.NoSuchFileException) return get("reason.noSuchFile");
        if (failure instanceof java.nio.file.AccessDeniedException) return get("reason.accessDenied");
        if (failure instanceof java.nio.file.FileAlreadyExistsException) return get("reason.fileExists");
        if (failure instanceof java.nio.file.FileSystemException fs && fs.getReason() != null && !fs.getReason().isBlank()) {
            return fs.getReason();
        }
        // java.io's form: "/path/file.pdf (No such file or directory)" - the part in brackets.
        if (failure instanceof java.io.FileNotFoundException && failure.getMessage() != null) {
            String m = failure.getMessage();
            int open = m.lastIndexOf(" (");
            if (open > 0 && m.endsWith(")")) return m.substring(open + 2, m.length() - 1);
        }
        return null;
    }

    /** The text for a key, its {@code {0}}, {@code {1}}... filled in. */
    public static String format(String key, Object... arguments) {
        return new MessageFormat(get(key), Locale.getDefault()).format(arguments);
    }
}
