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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Removes redacted terms from content that has no position on the page.
 * <p>
 * The burner handles everything a redaction rectangle can cover. This handles the rest, and the rest
 * is not a corner case: the same words a user redacted from a page routinely also sit in the bookmark
 * that points at it, in the comment somebody left on it, and in the document title. None of those are
 * drawn on the page, so no rectangle reaches them and no amount of burning removes them.
 * <p>
 * Nothing here has coordinates, so nothing can be burned; the strings are rewritten in place, with
 * each match replaced by {@link RedactionOptions#getMaskString()}. That keeps the structure working -
 * a bookmark still navigates, a field still has a value - while the content goes.
 *
 * @since 7.5.0
 */
public class RedactedStringRewriter {

    private static final Logger logger = Logger.getLogger(RedactedStringRewriter.class.getName());

    private final Document document;
    private final RedactionOptions options;
    private final RedactionReport report;
    private final TermMasker masker;

    public RedactedStringRewriter(Document document, RedactionRequest request, RedactionReport report) {
        this.document = document;
        this.options = request.getOptions();
        this.report = report;
        this.masker = new TermMasker(request.getTerms(), options.getMaskString());
    }

    /**
     * Rewrites every in-scope string the request's terms appear in.
     */
    public void rewrite() {
        if (options.redacts(RedactionTarget.OUTLINE)) {
            rewriteOutline();
        }
        if (options.redacts(RedactionTarget.ANNOTATION_CONTENTS)) {
            rewriteAnnotationContents();
        }
        if (options.redacts(RedactionTarget.FORM_VALUES)) {
            rewriteFormValues();
        }
        if (options.redacts(RedactionTarget.DESTINATIONS)) {
            rewriteDestinations();
        }
        if (options.redacts(RedactionTarget.METADATA)) {
            rewriteMetadata();
        }
    }

    /**
     * Values typed into form fields.
     * <p>
     * Both {@code /V} and {@code /DV}: the default value is a separate copy of the same string and
     * is easy to forget, so a field reset would put the redacted text straight back on the page.
     * Fields nest, and a value usually sits on the parent rather than on the widget that draws it,
     * so this walks the whole tree.
     */
    private void rewriteFormValues() {
        InteractiveForm form = document.getCatalog().getInteractiveForm();
        if (form == null || form.getFields() == null) {
            return;
        }
        Library library = document.getCatalog().getLibrary();
        for (Object field : form.getFields()) {
            rewriteField(field, library);
        }
    }

    private void rewriteField(Object field, Library library) {
        Object resolved = field instanceof Reference ? library.getObject((Reference) field) : field;
        if (resolved instanceof AbstractWidgetAnnotation) {
            rewriteFieldDictionary(((AbstractWidgetAnnotation<?>) resolved).getFieldDictionary(),
                    (AbstractWidgetAnnotation<?>) resolved, library);
        } else if (resolved instanceof FieldDictionary) {
            rewriteFieldDictionary((FieldDictionary) resolved, null, library);
        }
        if (resolved instanceof FieldDictionary) {
            List<Object> kids = ((FieldDictionary) resolved).getKids();
            if (kids != null) {
                for (Object kid : kids) {
                    rewriteField(kid, library);
                }
            }
        }
    }

    private void rewriteFieldDictionary(FieldDictionary field, AbstractWidgetAnnotation<?> widget,
                                        Library library) {
        if (field == null) {
            return;
        }
        // A field merged with its widget has its reference on the annotation, not on the field
        // dictionary built from the same entries, so the change has to be registered against
        // whichever of the two actually carries one.
        Reference reference = field.getPObjectReference() != null
                ? field.getPObjectReference()
                : (widget != null ? widget.getPObjectReference() : null);
        if (reference == null) {
            report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                    "A form field value matched a term but the field has no object reference, so " +
                            "the change could not be recorded");
            return;
        }
        boolean rewritten = maskFieldEntry(field, FieldDictionary.V_KEY, library, reference);
        rewritten |= maskFieldEntry(field, FieldDictionary.DV_KEY, library, reference);
        if (!rewritten) {
            return;
        }
        report.recordStringRewritten(RedactionTarget.FORM_VALUES);
        if (widget != null) {
            // The widget draws its value from a generated appearance stream, which still carries the
            // old text as glyphs. Regenerate it so what is drawn matches what the field now says.
            try {
                widget.resetAppearanceStream(new AffineTransform());
            } catch (RuntimeException e) {
                report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                        "Field value was masked but its appearance stream could not be regenerated, " +
                                "so the old text may still be drawn: " + widget.getPObjectReference());
            }
        }
    }

    private boolean maskFieldEntry(FieldDictionary field, Name key, Library library,
                                   Reference reference) {
        Object value = library.getObject(field.getEntries(), key);
        if (!(value instanceof StringObject)) {
            return false;
        }
        String text = Utils.convertStringObject(library, (StringObject) value);
        String masked = masker.mask(text);
        if (masked.equals(text)) {
            return false;
        }
        field.getEntries().put(key, new LiteralStringObject(masked, reference));
        library.getStateManager().addChange(new PObject(field, reference));
        return true;
    }

    /**
     * Named destination names.
     * <p>
     * The name is the leak - they are routinely made from the heading they point at - but it is also
     * the link target, quoted by every action that jumps there. Masking the name tree entry alone
     * would leave the name readable in each of those actions and break the links as well, so the
     * references are rewritten with it.
     */
    private void rewriteDestinations() {
        Names names = document.getCatalog().getNames();
        if (names == null || names.getDestsNameTree() == null) {
            return;
        }
        NameTree nameTree = names.getDestsNameTree();
        Map<String, String> renames = plannedRenames(nameTree);
        if (renames.isEmpty()) {
            return;
        }
        // Renaming in the node itself rather than through NameTree.updateNameNode: that also
        // rewrites the destination value, and only the name is being redacted - where a destination
        // points is not the secret, what it is called is.
        Library library = document.getCatalog().getLibrary();
        NameNode root = nameTree.getRoot();
        if (root.getPObjectReference() == null) {
            // The tree does not carry its own reference, so without this the rename would happen in
            // memory, go unrecorded, and be quietly dropped by the writer.
            root.setPObjectReference(library.getObjectReference(names.getEntries(), Names.DEST_KEY));
        }
        renameInNode(root, renames, library);
        rewriteDestinationReferences(renames);
    }

    /**
     * Applies the renames to a name tree node and, since a tree of any size is split across several,
     * to its children.
     */
    @SuppressWarnings("unchecked")
    private void renameInNode(NameNode node, Map<String, String> renames, Library library) {
        if (node == null) {
            return;
        }
        List<Object> namesAndValues = node.getNamesAndValues();
        if (namesAndValues != null) {
            boolean changed = false;
            for (int i = 0; i < namesAndValues.size(); i += 2) {
                Object name = namesAndValues.get(i);
                String text = name instanceof StringObject
                        ? Utils.convertStringObject(library, (StringObject) name)
                        : String.valueOf(name);
                String renamed = renames.get(text);
                if (renamed != null) {
                    namesAndValues.set(i, new LiteralStringObject(renamed, node.getPObjectReference()));
                    report.recordStringRewritten(RedactionTarget.DESTINATIONS);
                    changed = true;
                }
            }
            if (changed) {
                if (node.getPObjectReference() != null) {
                    library.getStateManager().addChange(new PObject(node, node.getPObjectReference()));
                } else {
                    report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                            "A named destination was renamed but its name tree node has no object " +
                                    "reference, so the change could not be written");
                }
            }
        }
        List<NameNode> kids = node.getKidsNodes();
        if (kids != null) {
            for (NameNode kid : kids) {
                renameInNode(kid, renames, library);
            }
        }
    }

    /**
     * Works out the new name for every destination whose name carries a term.
     * <p>
     * Two names differing only in the redacted part mask to the same string, so a counter keeps them
     * apart; without it the second rename would collide with the first and one destination would be
     * lost.
     */
    private Map<String, String> plannedRenames(NameTree nameTree) {
        Map<String, String> renames = new LinkedHashMap<>();
        List<?> namesAndValues = nameTree.getNamesAndValues();
        if (namesAndValues == null) {
            return renames;
        }
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < namesAndValues.size(); i += 2) {
            Object name = namesAndValues.get(i);
            String text = name instanceof StringObject
                    ? Utils.convertStringObject(document.getCatalog().getLibrary(), (StringObject) name)
                    : String.valueOf(name);
            taken.add(text);
        }
        for (String name : new ArrayList<>(taken)) {
            String masked = masker.mask(name);
            if (masked.equals(name)) {
                continue;
            }
            String candidate = masked;
            for (int suffix = 2; taken.contains(candidate); suffix++) {
                candidate = masked + " " + suffix;
            }
            taken.add(candidate);
            renames.put(name, candidate);
        }
        return renames;
    }

    /**
     * Points every reference at a renamed destination's new name. A named destination is quoted as a
     * string by whatever jumps to it, so these are both where the name survives and what would break
     * if only the name tree were rewritten.
     */
    private void rewriteDestinationReferences(Map<String, String> renames) {
        Library library = document.getCatalog().getLibrary();
        PageTree pageTree = document.getPageTree();
        for (int i = 0, max = pageTree.getNumberOfPages(); i < max; i++) {
            Page page = pageTree.getPage(i);
            List<Reference> references = page.getAnnotationReferences();
            if (references == null) {
                continue;
            }
            for (Reference reference : new ArrayList<>(references)) {
                Object annotation = library.getObject(reference);
                if (annotation instanceof Annotation) {
                    rewriteAnnotationDestination((Annotation) annotation, renames, library);
                }
            }
        }
        rewriteOutlineDestinations(document.getCatalog().getOutlines() != null
                ? document.getCatalog().getOutlines().getRootOutlineItem() : null, renames);
    }

    private void rewriteAnnotationDestination(Annotation annotation, Map<String, String> renames,
                                              Library library) {
        org.icepdf.core.pobjects.actions.Action action = annotation.getAction();
        if (!(action instanceof GoToAction)) {
            return;
        }
        GoToAction goToAction = (GoToAction) action;
        Destination destination = goToAction.getDestination();
        String renamed = renamedName(destination, renames);
        if (renamed != null) {
            // A fresh Destination rather than the mutated one: both setters skip the work when the
            // value they are handed equals what is already there, and mutating the instance they
            // just returned makes that check trivially true.
            goToAction.setDestination(namedDestination(library, renamed, annotation.getPObjectReference()));
            // An inline /A dictionary has no reference of its own, so the change belongs to the
            // annotation that carries it.
            Reference reference = goToAction.getPObjectReference() != null
                    ? goToAction.getPObjectReference() : annotation.getPObjectReference();
            if (reference != null) {
                library.getStateManager().addChange(new PObject(
                        goToAction.getPObjectReference() != null ? goToAction : annotation, reference));
            }
        }
    }

    private void rewriteOutlineDestinations(OutlineItem item, Map<String, String> renames) {
        if (item == null) {
            return;
        }
        String renamed = renamedName(item.getDest(), renames);
        if (renamed != null) {
            item.setDest(namedDestination(document.getCatalog().getLibrary(), renamed,
                    item.getPObjectReference()));
        }
        for (int i = 0, max = item.getSubItemCount(); i < max; i++) {
            rewriteOutlineDestinations(item.getSubItem(i), renames);
        }
    }

    private Destination namedDestination(Library library, String name, Reference owner) {
        return new Destination(library, new LiteralStringObject(name, owner));
    }

    private String renamedName(Destination destination, Map<String, String> renames) {
        if (destination == null || destination.getNamedDestination() == null) {
            return null;
        }
        return renames.get(destination.getNamedDestination());
    }

    /**
     * Bookmark titles. Usually the cheapest place a redacted heading survives, since a bookmark is
     * generally made from the heading it points at.
     */
    private void rewriteOutline() {
        Outlines outlines = document.getCatalog().getOutlines();
        if (outlines == null) {
            return;
        }
        rewriteOutlineItem(outlines.getRootOutlineItem());
    }

    private void rewriteOutlineItem(OutlineItem item) {
        if (item == null) {
            return;
        }
        String title = item.getTitle();
        if (title != null) {
            String masked = masker.mask(title);
            if (!masked.equals(title)) {
                item.setTitle(masked);
                report.recordStringRewritten(RedactionTarget.OUTLINE);
            }
        }
        for (int i = 0, max = item.getSubItemCount(); i < max; i++) {
            rewriteOutlineItem(item.getSubItem(i));
        }
    }

    /**
     * The text of markup annotations - a comment, a sticky note.
     * <p>
     * Note what this does not do: an annotation's appearance stream is generated from its contents
     * and carries the same words as drawn glyphs. Those are reached by geometry, when the burner
     * descends into appearance streams, not from here.
     */
    private void rewriteAnnotationContents() {
        Library library = document.getCatalog().getLibrary();
        PageTree pageTree = document.getPageTree();
        for (int i = 0, max = pageTree.getNumberOfPages(); i < max; i++) {
            Page page = pageTree.getPage(i);
            List<Reference> references = page.getAnnotationReferences();
            if (references == null) {
                continue;
            }
            for (Reference reference : new ArrayList<>(references)) {
                Object annotation = library.getObject(reference);
                if (annotation instanceof MarkupAnnotation) {
                    rewriteAnnotation((MarkupAnnotation) annotation, library);
                }
            }
        }
    }

    private void rewriteAnnotation(MarkupAnnotation annotation, Library library) {
        String contents = annotation.getContents();
        if (contents == null) {
            return;
        }
        String masked = masker.mask(contents);
        if (!masked.equals(contents)) {
            annotation.setContents(masked);
            library.getStateManager().addChange(
                    new PObject(annotation, annotation.getPObjectReference()));
            report.recordStringRewritten(RedactionTarget.ANNOTATION_CONTENTS);
        }
    }

    /**
     * The document information dictionary. Title, subject, keywords and author routinely repeat
     * whatever the document is about, which is frequently the thing being redacted.
     */
    private void rewriteMetadata() {
        PInfo info = document.getInfo();
        if (info == null) {
            return;
        }
        // The typed setters rather than setProperty: they run the value through the document's
        // encryption, which writing the string straight into the dictionary would skip.
        rewriteInfoEntry(info.getTitle(), info::setTitle);
        rewriteInfoEntry(info.getAuthor(), info::setAuthor);
        rewriteInfoEntry(info.getSubject(), info::setSubject);
        rewriteInfoEntry(info.getKeywords(), info::setKeywords);
    }

    private void rewriteInfoEntry(String value, Consumer<String> setter) {
        if (value == null) {
            return;
        }
        String masked = masker.mask(value);
        if (!masked.equals(value)) {
            setter.accept(masked);
            report.recordStringRewritten(RedactionTarget.METADATA);
        }
    }
}
