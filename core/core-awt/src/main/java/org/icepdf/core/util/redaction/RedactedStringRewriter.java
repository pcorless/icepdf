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
import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.awt.geom.AffineTransform;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * @since 8.0
 */
public class RedactedStringRewriter {

    private static final Logger logger = Logger.getLogger(RedactedStringRewriter.class.getName());

    private final Document document;
    private final RedactionOptions options;
    private final RedactionReport report;
    private final TermMasker masker;

    /** A field's strings that decide what it shows, so a widget drawing them has to be redrawn. */
    private static final Name[] FIELD_VALUE_KEYS = {FieldDictionary.V_KEY, FieldDictionary.DV_KEY,
            VariableTextFieldDictionary.RV_KEY, ChoiceFieldDictionary.OPT_KEY};
    /** A field's other strings, which aren't drawn. */
    private static final Name[] FIELD_NAME_KEYS = {FieldDictionary.T_KEY, FieldDictionary.TU_KEY,
            FieldDictionary.TM_KEY, Annotation.CONTENTS_KEY};
    private static final Pattern HEX_STRING = Pattern.compile("<([0-9A-Fa-f\\s]+)>");

    private static final Name STRUCT_TREE_ROOT_KEY = new Name("StructTreeRoot");
    private static final Name KIDS_KEY = new Name("K");
    /** The structure-tree entries that carry text a redaction has to reach. */
    private static final Name[] STRUCTURE_TEXT_KEYS = {
            new Name("ActualText"), new Name("Alt"), new Name("E"), new Name("T")};

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
        if (options.redacts(RedactionTarget.LINKS)) {
            rewriteLinks();
        }
        if (options.redacts(RedactionTarget.TAGGED_TEXT)) {
            rewriteTaggedText();
        }
        if (options.redacts(RedactionTarget.METADATA)) {
            rewriteMetadata();
        }
        if (options.redacts(RedactionTarget.ATTACHMENTS)) {
            handleAttachments();
        }
    }


    /**
     * The structure tree's text: {@code /ActualText}, {@code /Alt}, {@code /E} and {@code /T}.
     * <p>
     * A tagged PDF carries a second copy of its own words outside the content streams. {@code /Alt}
     * and {@code /ActualText} exist precisely so a reader can say or copy something other than the
     * glyphs - which means burning the glyphs off the page leaves the sentence intact in the
     * structure tree, where assistive technology and "copy text" will still find it. {@code /E} is
     * the expansion of an abbreviation and {@code /T} the element's title, both of which routinely
     * repeat the heading they belong to.
     * <p>
     * The tree is walked as raw dictionaries because the core has no structure-tree model to walk.
     * Only elements carrying one of these strings are touched; the tree's shape is left alone, so
     * the tagging still describes the document.
     */
    private void rewriteTaggedText() {
        Library library = document.getCatalog().getLibrary();
        Object root = library.getObject(document.getCatalog().getEntries(), STRUCT_TREE_ROOT_KEY);
        Reference owner = null;
        if (root instanceof Reference) {
            owner = (Reference) root;
            root = library.getObject(owner);
        }
        if (root instanceof DictionaryEntries) {
            rewriteStructureElement((DictionaryEntries) root, owner, library, new HashSet<>());
        }
    }

    /**
     * @param element the structure element, or the tree root, being visited
     * @param owner   the reference of the object the element's bytes live in, which is what a change
     *                has to be registered against - an element written inline in its parent has no
     *                reference of its own, so the parent's carries it
     * @param visited references already seen; a malformed tree can name the same element twice, and
     *                {@code /K} is not guaranteed to be acyclic
     */
    private void rewriteStructureElement(DictionaryEntries element, Reference owner, Library library,
                                         Set<Reference> visited) {
        boolean rewritten = false;
        for (Name key : STRUCTURE_TEXT_KEYS) {
            Object value = library.getObject(element, key);
            if (!(value instanceof StringObject)) {
                continue;
            }
            String text = TermMasker.decodeString(library, (StringObject) value);
            String masked = masker.mask(text);
            if (!masked.equals(text)) {
                element.put(key, new LiteralStringObject(masked, owner));
                report.recordStringRewritten(RedactionTarget.TAGGED_TEXT);
                rewritten = true;
            }
        }
        if (rewritten) {
            if (owner != null) {
                library.getStateManager().addChange(
                        new PObject(new Dictionary(library, element), owner));
            } else {
                report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                        "Tagged text was masked in a structure element with no object reference, " +
                                "so the change could not be written");
            }
        }
        Object kids = library.getObject(element, KIDS_KEY);
        if (kids instanceof List) {
            for (Object kid : (List<?>) kids) {
                rewriteStructureKid(kid, owner, library, visited);
            }
        } else {
            rewriteStructureKid(element.get(KIDS_KEY), owner, library, visited);
        }
    }

    /**
     * A {@code /K} entry is a mixed bag: a child element, a reference to one, a marked-content
     * identifier (an integer, which is a pointer into a content stream and holds no text), or an
     * object reference dictionary. Only the first two are worth following.
     */
    private void rewriteStructureKid(Object kid, Reference owner, Library library, Set<Reference> visited) {
        if (kid instanceof Reference) {
            Reference reference = (Reference) kid;
            if (!visited.add(reference)) {
                return;
            }
            Object resolved = library.getObject(reference);
            if (resolved instanceof DictionaryEntries) {
                rewriteStructureElement((DictionaryEntries) resolved, reference, library, visited);
            } else if (resolved instanceof Dictionary) {
                rewriteStructureElement(((Dictionary) resolved).getEntries(), reference, library, visited);
            }
        } else if (kid instanceof DictionaryEntries) {
            // Written inline, so it has no reference of its own and belongs to whatever object does.
            rewriteStructureElement((DictionaryEntries) kid, owner, library, visited);
        }
    }

    /**
     * Attached files.
     * <p>
     * Nothing here can be masked: an attachment is an arbitrary file, and there is no general way to
     * find a term inside a spreadsheet or an image, let alone remove it. An attachment is also
     * frequently the source of the document it is attached to, which makes leaving one the plainest
     * leak available. So the choice is to remove them or to say plainly that they were not checked -
     * which is what {@link RedactionOptions#isRemoveAttachments()} selects between.
     */
    private void handleAttachments() {
        Names names = document.getCatalog().getNames();
        if (names == null || names.getEmbeddedFilesNameTree() == null) {
            return;
        }
        NameTree attachments = names.getEmbeddedFilesNameTree();
        List<?> namesAndValues = attachments.getNamesAndValues();
        int count = namesAndValues == null ? 0 : namesAndValues.size() / 2;
        if (count == 0) {
            return;
        }
        Library library = document.getCatalog().getLibrary();
        if (!options.isRemoveAttachments()) {
            report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                    count + " attached file(s) were kept and cannot be redacted; their contents were "
                            + "not examined");
            return;
        }
        names.getEntries().remove(Names.EMBEDDED_FILES_KEY);
        library.getStateManager().addChange(new PObject(names, names.getPObjectReference()));
        report.recordStringRewritten(RedactionTarget.ATTACHMENTS);
        report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                "Removed " + count + " attached file(s): an attachment cannot be redacted, and is "
                        + "often the source of the document it is attached to");
    }

    /**
     * The strings of form fields: values typed in, and the names and options around them.
     * <p>
     * Both {@code /V} and {@code /DV}: the default value is a separate copy of the same string and
     * is easy to forget, so a field reset would put the redacted text straight back on the page.
     * Rich text, a choice field's options, the field's names and a widget's own {@code /Contents}
     * routinely repeat the value too. A value may be a string, an array or a text stream. A masked
     * name can no longer be found by an action or a script that asks for it by name.
     * Fields nest, and a value usually sits on the parent rather than on the widget that draws it,
     * so this walks the whole tree. A widget that inherits a masked value is regenerated too.
     */
    private void rewriteFormValues() {
        InteractiveForm form = document.getCatalog().getInteractiveForm();
        if (form == null || form.getFields() == null) {
            return;
        }
        Library library = document.getCatalog().getLibrary();
        for (Object field : form.getFields()) {
            rewriteField(field, library, false);
        }
    }

    /**
     * @param inheritedValueMasked whether an ancestor's value was masked, which a field without its
     *                             own value inherits
     */
    private void rewriteField(Object field, Library library, boolean inheritedValueMasked) {
        Object resolved = field instanceof Reference ? library.getObject((Reference) field) : field;
        if (resolved instanceof AbstractWidgetAnnotation) {
            rewriteFieldDictionary(((AbstractWidgetAnnotation<?>) resolved).getFieldDictionary(),
                    (AbstractWidgetAnnotation<?>) resolved, library, inheritedValueMasked);
        } else if (resolved instanceof FieldDictionary) {
            FieldDictionary dictionary = (FieldDictionary) resolved;
            boolean masked = rewriteFieldDictionary(dictionary, null, library, false);
            List<Object> kids = dictionary.getKids();
            if (kids != null) {
                boolean passedDown = masked || (inheritedValueMasked && !dictionary.hasFieldValue());
                for (Object kid : kids) {
                    rewriteField(kid, library, passedDown);
                }
            }
        }
    }

    /**
     * @return true when a string of this field that a widget draws was masked
     */
    private boolean rewriteFieldDictionary(FieldDictionary field, AbstractWidgetAnnotation<?> widget,
                                           Library library, boolean inheritedValueMasked) {
        if (field == null) {
            return false;
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
            return false;
        }
        boolean rewritten = false;
        for (Name key : FIELD_VALUE_KEYS) {
            rewritten |= maskFieldEntry(field, key, library, reference);
        }
        boolean nameRewritten = false;
        for (Name key : FIELD_NAME_KEYS) {
            nameRewritten |= maskFieldEntry(field, key, library, reference);
        }
        if (rewritten || nameRewritten) {
            report.recordStringRewritten(RedactionTarget.FORM_VALUES);
        }
        if (widget != null && (rewritten || (inheritedValueMasked && !field.hasFieldValue())
                || drawsTerm(field, library))) {
            // The widget draws its value from a generated appearance stream, which still carries the
            // old text as glyphs. Regenerate it so what is drawn matches what the field now says.
            // A widget without /Type /Annot is built here, apart from the page's instance and not yet
            // initialised, and with no appearance loaded the reset would quietly do nothing.
            try {
                maskChoiceOptions(field);
                widget.init();
                widget.resetAppearanceStream(new AffineTransform());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                        "Interrupted before a masked field's appearance stream was regenerated, " +
                                "so the old text may still be drawn: " + widget.getPObjectReference());
            } catch (RuntimeException e) {
                report.warn(RedactionWarning.Kind.UNSUPPORTED_CONTENT,
                        "Field value was masked but its appearance stream could not be regenerated, " +
                                "so the old text may still be drawn: " + widget.getPObjectReference());
            }
        }
        return rewritten;
    }

    private boolean maskFieldEntry(FieldDictionary field, Name key, Library library,
                                   Reference reference) {
        Object value = field.getEntries().get(key);
        Object masked = maskedValue(value, library, reference);
        if (masked == value) {
            return false;
        }
        field.getEntries().put(key, masked);
        library.getStateManager().addChange(new PObject(field, reference));
        return true;
    }

    /**
     * @return the value with its strings masked, or the same instance when nothing matched. A text
     * stream comes back as a string, and the stream leaves the file with the last reference to it.
     */
    private Object maskedValue(Object value, Library library, Reference reference) {
        Object resolved = value instanceof Reference ? library.getObject((Reference) value) : value;
        String text = null;
        if (resolved instanceof StringObject) {
            text = TermMasker.decodeString(library, (StringObject) resolved);
        } else if (resolved instanceof Stream) {
            byte[] bytes = ((Stream) resolved).getDecodedStreamBytes();
            text = bytes != null ? TermMasker.decodeText(bytes) : null;
        } else if (resolved instanceof List) {
            List<Object> masked = new ArrayList<>();
            boolean changed = false;
            for (Object element : (List<?>) resolved) {
                Object maskedElement = maskedValue(element, library, reference);
                changed |= maskedElement != element;
                masked.add(maskedElement);
            }
            return changed ? masked : value;
        }
        if (text == null) {
            return value;
        }
        String masked = masker.mask(text);
        return masked.equals(text) ? value : new LiteralStringObject(masked, reference);
    }

    /** A choice field read its options when it was built, and draws those rather than {@code /Opt}. */
    private void maskChoiceOptions(FieldDictionary field) {
        if (field instanceof ChoiceFieldDictionary && ((ChoiceFieldDictionary) field).getOptions() != null) {
            for (ChoiceFieldDictionary.ChoiceOption option : ((ChoiceFieldDictionary) field).getOptions()) {
                option.setLabel(masker.mask(option.getLabel()));
                option.setValue(masker.mask(option.getValue()));
            }
        }
    }

    /** Whether the field's appearance draws a term, though nothing that was masked said so. */
    private boolean drawsTerm(FieldDictionary field, Library library) {
        Object appearances = library.getObject(field.getEntries(), Annotation.APPEARANCE_STREAM_KEY);
        if (!(appearances instanceof DictionaryEntries)) {
            return false;
        }
        Object normal = library.getObject((DictionaryEntries) appearances, Annotation.APPEARANCE_STREAM_NORMAL_KEY);
        if (normal instanceof DictionaryEntries) {
            // a button's normal appearance has one stream per state
            for (Object state : ((DictionaryEntries) normal).values()) {
                Object resolved = state instanceof Reference ? library.getObject((Reference) state) : state;
                if (resolved instanceof Stream && streamHoldsTerm((Stream) resolved)) {
                    return true;
                }
            }
            return false;
        }
        return normal instanceof Stream && streamHoldsTerm((Stream) normal);
    }

    private boolean streamHoldsTerm(Stream stream) {
        byte[] bytes = stream.getDecodedStreamBytes();
        if (bytes == null) {
            return false;
        }
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        if (masker.matches(content)) {
            return true;
        }
        Matcher hex = HEX_STRING.matcher(content);
        while (hex.find()) {
            String digits = hex.group(1).replaceAll("\\s", "");
            byte[] decoded = new byte[digits.length() / 2];
            for (int i = 0; i < decoded.length; i++) {
                decoded[i] = (byte) Integer.parseInt(digits.substring(2 * i, 2 * i + 2), 16);
            }
            if (masker.matches(new String(decoded, StandardCharsets.ISO_8859_1))
                    || masker.matches(TermMasker.decodeText(decoded))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refuses a redaction that a signature stands in the way of.
     * <p>
     * A signature field's value is a signature dictionary: the signer's name, location, reason and
     * contact details, and the signature itself, whose certificates name the signer again. Its
     * appearance usually draws the name too. The certificates can't be masked, and a masked name would
     * no longer match them, so a signature that holds a term, or whose appearance draws one, can't be
     * redacted. Removing it would quietly drop the record that the document was signed, so the
     * redaction is refused instead, before anything is written. A signature without a term is left
     * alone.
     *
     * @throws RedactionRefusedException naming the first signature field found with a term
     */
    public void refuseSignaturesWithTerms() throws RedactionRefusedException {
        if (!options.redacts(RedactionTarget.FORM_VALUES)) {
            return;
        }
        InteractiveForm form = document.getCatalog().getInteractiveForm();
        if (form == null || form.getFields() == null) {
            return;
        }
        Library library = document.getCatalog().getLibrary();
        Set<Reference> visited = new HashSet<>();
        for (Object field : form.getFields()) {
            refuseSignatureWithTerm(field, library, visited, 0);
        }
    }

    private void refuseSignatureWithTerm(Object field, Library library, Set<Reference> visited, int depth)
            throws RedactionRefusedException {
        if (depth > 32 || (field instanceof Reference && !visited.add((Reference) field))) {
            return;
        }
        Object resolved = field instanceof Reference ? library.getObject((Reference) field) : field;
        FieldDictionary dictionary = resolved instanceof AbstractWidgetAnnotation
                ? ((AbstractWidgetAnnotation<?>) resolved).getFieldDictionary()
                : resolved instanceof FieldDictionary ? (FieldDictionary) resolved : null;
        if (dictionary == null) {
            return;
        }
        if (FieldDictionaryFactory.TYPE_SIGNATURE.equals(dictionary.getFieldType())) {
            Object value = library.getObject(dictionary.getEntries(), FieldDictionary.V_KEY);
            boolean signatureHoldsTerm = entriesOf(value) != null && holdsTerm(entriesOf(value), library, 0);
            if (signatureHoldsTerm || appearanceDrawsTerm(dictionary.getEntries(), library)) {
                String name = dictionary.getFullyQualifiedFieldName();
                // the name is the document's text too, so a term in it is masked here as well
                throw new RedactionRefusedException(name != null ? masker.mask(name) : null);
            }
        }
        if (!(resolved instanceof AbstractWidgetAnnotation) && dictionary.getKids() != null) {
            for (Object kid : dictionary.getKids()) {
                refuseSignatureWithTerm(kid, library, visited, depth + 1);
            }
        }
    }

    /**
     * Whether a string in a signature dictionary, or in a dictionary or array written inline in it,
     * holds a term. Each string is read as text and as its bytes, as the signature in
     * {@code /Contents} is binary, with the signer's name in its certificates.
     */
    private boolean holdsTerm(Object value, Library library, int depth) {
        if (depth > 8) {
            return false;
        }
        if (value instanceof StringObject) {
            StringObject string = (StringObject) value;
            byte[] decrypted = string.getDecryptedRawBytes(library.getSecurityManager());
            byte[] raw = string.getRawBytes();
            return masker.matches(TermMasker.decodeString(library, string))
                    || (decrypted != null && masker.matches(new String(decrypted, StandardCharsets.ISO_8859_1)))
                    // a signature's /Contents is never encrypted
                    || (raw != null && masker.matches(new String(raw, StandardCharsets.ISO_8859_1)));
        }
        if (value instanceof Dictionary) {
            value = ((Dictionary) value).getEntries();
        }
        if (value instanceof DictionaryEntries) {
            for (Object entry : ((DictionaryEntries) value).values()) {
                if (holdsTerm(entry, library, depth + 1)) {
                    return true;
                }
            }
        } else if (value instanceof List) {
            for (Object element : (List<?>) value) {
                if (holdsTerm(element, library, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether any of a widget's appearances draws a term, including in the forms they draw: a
     * signature's appearance usually draws its text from a nested form, {@code /n2}.
     */
    private boolean appearanceDrawsTerm(DictionaryEntries widget, Library library) {
        Object appearances = library.getObject(widget, Annotation.APPEARANCE_STREAM_KEY);
        if (!(appearances instanceof DictionaryEntries)) {
            return false;
        }
        Set<Reference> visited = new HashSet<>();
        for (Object appearance : ((DictionaryEntries) appearances).values()) {
            Object resolved = appearance instanceof Reference ? library.getObject((Reference) appearance) : appearance;
            if (resolved instanceof DictionaryEntries) {
                // one appearance per state
                for (Object state : ((DictionaryEntries) resolved).values()) {
                    if (formDrawsTerm(state, library, visited, 0)) {
                        return true;
                    }
                }
            } else if (formDrawsTerm(appearance, library, visited, 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean formDrawsTerm(Object form, Library library, Set<Reference> visited, int depth) {
        if (depth > 8 || (form instanceof Reference && !visited.add((Reference) form))) {
            return false;
        }
        Object resolved = form instanceof Reference ? library.getObject((Reference) form) : form;
        if (!(resolved instanceof Form)) {
            return false;
        }
        if (streamHoldsTerm((Stream) resolved)) {
            return true;
        }
        DictionaryEntries resources = entriesOf(library.getObject(((Form) resolved).getEntries(), Page.RESOURCES_KEY));
        DictionaryEntries xObjects = resources != null
                ? entriesOf(library.getObject(resources, Resources.XOBJECT_KEY)) : null;
        if (xObjects != null) {
            for (Object xObject : xObjects.values()) {
                if (formDrawsTerm(xObject, library, visited, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static DictionaryEntries entriesOf(Object value) {
        return value instanceof Dictionary ? ((Dictionary) value).getEntries()
                : value instanceof DictionaryEntries ? (DictionaryEntries) value : null;
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
                        ? TermMasker.decodeString(library, (StringObject) name)
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
                    ? TermMasker.decodeString(document.getCatalog().getLibrary(), (StringObject) name)
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
     * The addresses URI actions open.
     * <p>
     * A link's address often repeats what it is about: a person's page, a {@code mailto:} address, a
     * search for a name. The action can sit on a link, in a link's {@code /PA}, in an annotation's or
     * a field's additional actions, on a bookmark, in the document's open action or in a chain of
     * {@code /Next} actions, so every object in the file is looked at rather than each of those
     * places. A masked address no longer goes where it did.
     */
    private void rewriteLinks() {
        Library library = document.getCatalog().getLibrary();
        Set<Reference> visited = new HashSet<>();
        for (CrossReference crossReference : library.getCrossReferenceRoot().getCrossReferences()) {
            for (Reference reference : crossReference.getEntries().keySet()) {
                if (!visited.add(reference)) {
                    continue;
                }
                Object object = library.getObject(reference);
                DictionaryEntries entries = object instanceof Dictionary ? ((Dictionary) object).getEntries()
                        : object instanceof DictionaryEntries ? (DictionaryEntries) object : null;
                if (entries != null && maskUris(entries, reference, library)) {
                    Object changed = object instanceof Dictionary ? object : new Dictionary(library, entries);
                    library.getStateManager().addChange(new PObject(changed, reference));
                }
            }
        }
    }

    /**
     * Masks the URI actions in a value and the dictionaries and arrays written inline in it. A
     * reference is an object of its own, which {@link #rewriteLinks()} reaches anyway.
     *
     * @param owner the object the value is written in, which a masked string is encrypted for
     * @return true when something was masked
     */
    private boolean maskUris(Object value, Reference owner, Library library) {
        boolean masked = false;
        if (value instanceof DictionaryEntries) {
            DictionaryEntries entries = (DictionaryEntries) value;
            if (Action.ACTION_TYPE_URI.equals(entries.get(Action.ACTION_TYPE_KEY))
                    && entries.get(URIAction.URI_KEY) instanceof StringObject) {
                String uri = TermMasker.decodeString(library, (StringObject) entries.get(URIAction.URI_KEY));
                String maskedUri = masker.mask(uri);
                if (!maskedUri.equals(uri)) {
                    entries.put(URIAction.URI_KEY, new LiteralStringObject(maskedUri, owner));
                    report.recordStringRewritten(RedactionTarget.LINKS);
                    masked = true;
                }
            }
            for (Object entry : entries.values()) {
                masked |= maskUris(entry, owner, library);
            }
        } else if (value instanceof Dictionary) {
            masked = maskUris(((Dictionary) value).getEntries(), owner, library);
        } else if (value instanceof List) {
            for (Object element : (List<?>) value) {
                masked |= maskUris(element, owner, library);
            }
        }
        return masked;
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

    /**
     * A comment carries its text in more than one place: the plain {@code /Contents}, a rich text
     * copy in {@code /RC}, and the commenter's name in {@code /T}. Redacting only the first leaves
     * the same words in the second, and a name is often exactly what a redaction is for.
     */
    private void rewriteAnnotation(MarkupAnnotation annotation, Library library) {
        boolean rewritten = maskAnnotationEntry(annotation.getContents(), annotation::setContents);
        // Read from the dictionary rather than through getRichText(), which returns a field only
        // filled in by init(): an annotation reached through the library has not necessarily been
        // initialised, and would silently report no rich text at all.
        rewritten |= maskAnnotationEntry(stringEntry(annotation, MarkupAnnotation.RC_KEY, library),
                annotation::setRichText);
        rewritten |= maskAnnotationEntry(annotation.getTitleText(), annotation::setTitleText);
        if (rewritten) {
            library.getStateManager().addChange(
                    new PObject(annotation, annotation.getPObjectReference()));
            report.recordStringRewritten(RedactionTarget.ANNOTATION_CONTENTS);
        }
    }

    /**
     * Reads a text entry straight out of a dictionary, without depending on whatever lazy field the
     * owning object may or may not have filled in.
     */
    private String stringEntry(Dictionary dictionary, Name key, Library library) {
        Object value = library.getObject(dictionary.getEntries(), key);
        return value instanceof StringObject
                ? TermMasker.decodeString(library, (StringObject) value) : null;
    }

    private boolean maskAnnotationEntry(String value, Consumer<String> setter) {
        if (value == null) {
            return false;
        }
        String masked = masker.mask(value);
        if (masked.equals(value)) {
            return false;
        }
        setter.accept(masked);
        return true;
    }

    /**
     * The document information dictionary. Title, subject, keywords and author routinely repeat
     * whatever the document is about, which is frequently the thing being redacted.
     */
    private void rewriteMetadata() {
        PInfo info = document.getInfo();
        if (info != null) {
            // The typed setters rather than setProperty: they run the value through the document's
            // encryption, which writing the string straight into the dictionary would skip.
            rewriteInfoEntry(info.getTitle(), info::setTitle);
            rewriteInfoEntry(info.getAuthor(), info::setAuthor);
            rewriteInfoEntry(info.getSubject(), info::setSubject);
            rewriteInfoEntry(info.getKeywords(), info::setKeywords);
        }
        // Outside the guard above: the two are independent, and a document can carry XMP with no
        // information dictionary at all.
        rewriteXmpMetadata();
    }

    /**
     * The XMP metadata stream.
     * <p>
     * Separate from the information dictionary and usually a duplicate of it - title, subject,
     * keywords and author again, in XML - so redacting only {@code /Info} leaves the same words
     * sitting in a stream a text editor will show at a glance.
     * <p>
     * Masked as text rather than parsed as XML: the terms are content, they appear inside element
     * text and attribute values, and replacing them there needs no understanding of the schema.
     * Anything that would break the XML - a term matching a tag name - is a term the caller has
     * chosen to remove from the document anyway.
     */
    private void rewriteXmpMetadata() {
        Stream metadata = document.getCatalog().getMetaData();
        if (metadata == null) {
            return;
        }
        byte[] raw = metadata.getDecodedStreamBytes();
        if (raw == null || raw.length == 0) {
            return;
        }
        String xmp = new String(raw, StandardCharsets.UTF_8);
        String masked = masker.mask(xmp);
        if (masked.equals(xmp)) {
            return;
        }
        metadata.setRawBytes(masked.getBytes(StandardCharsets.UTF_8));
        Library library = document.getCatalog().getLibrary();
        library.getStateManager().addChange(new PObject(metadata, metadata.getPObjectReference()));
        report.recordStringRewritten(RedactionTarget.METADATA);
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
