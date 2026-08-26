#!/usr/bin/env python3
"""Generate the minimal PDFs the redaction golden tests run against.

Every fixture is small, hand-built and exercises one text-layout feature, so a golden diff points
at a single cause.  No third-party dependency, matching blending/make_iso_fixture.py.

Determinism matters more here than realism.  The fonts are non-embedded Helvetica, which ICEpdf
substitutes with whatever the host has, so glyph advances would otherwise vary by machine and the
goldens with them.  Each font therefore carries an explicit /Widths array with a single uniform
width, which ICEpdf applies over the substituted face: every glyph advances exactly
WIDTH/1000 * fontsize, on any machine.

Run from this directory:  python3 make_redaction_fixtures.py
"""

import os

WIDTH = 500  # uniform glyph width, in 1/1000 text space units


def build(objs, root=1):
    """Serialise numbered objects into a PDF with a plain xref table."""
    out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objs[num] + b"\nendobj\n"
    xref = len(out)
    top = max(objs) + 1
    out += b"xref\n0 %d\n" % top
    out += b"0000000000 65535 f \n"
    for num in range(1, top):
        if num in offsets:
            out += b"%010d 00000 n \n" % offsets[num]
        else:
            out += b"0000000000 65535 f \n"
    out += b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (top, root, xref)
    return bytes(out)


def stream_obj(data, extra=b""):
    return b"<< /Length %d %s>>\nstream\n" % (len(data), extra) + data + b"\nendstream"


def helvetica(name=b"/Helvetica"):
    """A simple font with uniform, explicit widths - see the note about determinism above."""
    widths = b"[" + b" ".join(b"%d" % WIDTH for _ in range(32, 127)) + b"]"
    return (b"<< /Type /Font /Subtype /Type1 /BaseFont " + name +
            b" /FirstChar 32 /LastChar 126 /Widths " + widths +
            b" /Encoding /WinAnsiEncoding >>")


def simple_page(content, extra_resources=b"", extra_objs=None, page_extra=b""):
    """One page, one font (/F1), one content stream."""
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Resources << /Font << /F1 5 0 R >> " + extra_resources + b" >> " + page_extra + b">>"),
        4: stream_obj(content),
        5: helvetica(),
    }
    if extra_objs:
        objs.update(extra_objs)
    return build(objs)


# Fixtures the viewer's tests need too.  Written to both places rather than copied by hand, so a
# regenerated fixture cannot silently go stale in one of them.
VIEWER_COPIES = {
    "positionless_text.pdf": "../../../../../../viewer/viewer-awt/src/test/resources/redact",
}


def write(name, data):
    with open(name, "wb") as handle:
        handle.write(data)
    print("wrote %-28s %5d bytes" % (name, len(data)))
    viewer_dir = VIEWER_COPIES.get(name)
    if viewer_dir and os.path.isdir(viewer_dir):
        viewer_path = os.path.join(viewer_dir, name)
        with open(viewer_path, "wb") as handle:
            handle.write(data)
        print("  also   %-28s (viewer tests)" % viewer_path)


# -- fixtures ------------------------------------------------------------------------------------

def simple_tj():
    """Baseline: one Tj, one font, nothing else moving."""
    return simple_page(b"BT\n/F1 12 Tf\n20 100 Td\n(alpha bravo charlie) Tj\nET\n")


def tj_array():
    """TJ with kerning numbers between the elements, which the writer has to preserve or absorb."""
    return simple_page(b"BT\n/F1 12 Tf\n20 100 Td\n"
                       b"[(alpha) -250 (bravo) -500 (charlie)] TJ\nET\n")


def quote_operators():
    """' and " - the operators the callback does not treat as text-layout tokens (GH-525)."""
    return simple_page(b"BT\n/F1 12 Tf\n14 TL\n20 150 Td\n"
                       b"(alpha bravo) Tj\n"
                       b"(charlie delta) '\n"
                       b"1 2 (echo foxtrot) \"\n"
                       b"ET\n")


def text_state():
    """Non-default Tz, Tc, Tw and Ts, all of which shift where a rewritten glyph lands."""
    return simple_page(b"BT\n/F1 12 Tf\n50 Tz\n2 Tc\n5 Tw\n20 100 Td\n"
                       b"(alpha bravo charlie) Tj\n"
                       b"3 Ts\n(delta echo) Tj\n"
                       b"ET\n")


def form_xobject():
    """Text inside a form with a non-identity /Matrix, which exercises createChildInstance."""
    form_content = b"BT\n/F1 12 Tf\n0 0 Td\n(alpha bravo charlie) Tj\nET\n"
    form = (b"<< /Type /XObject /Subtype /Form /FormType 1 /BBox [0 0 300 50] "
            b"/Matrix [1 0 0 1 20 120] /Resources << /Font << /F1 5 0 R >> >> " +
            b"/Length %d >>\nstream\n" % len(form_content) + form_content + b"\nendstream")
    return simple_page(b"BT\n/F1 12 Tf\n20 40 Td\n(page level text) Tj\nET\n/Fm0 Do\n",
                       extra_resources=b"/XObject << /Fm0 6 0 R >>",
                       extra_objs={6: form})


def form_drawn_twice():
    """The same form at two positions - one shared PageText, two placements."""
    form_content = b"BT\n/F1 12 Tf\n0 0 Td\n(repeated text) Tj\nET\n"
    form = (b"<< /Type /XObject /Subtype /Form /FormType 1 /BBox [0 0 300 50] "
            b"/Resources << /Font << /F1 5 0 R >> >> " +
            b"/Length %d >>\nstream\n" % len(form_content) + form_content + b"\nendstream")
    return simple_page(b"q 1 0 0 1 20 150 cm /Fm0 Do Q\nq 1 0 0 1 20 60 cm /Fm0 Do Q\n",
                       extra_resources=b"/XObject << /Fm0 6 0 R >>",
                       extra_objs={6: form})


def multi_stream():
    """/Contents as an array, divided between a show operator's operand and the operator itself.

    PDF 32000-1 7.8.2 allows a division only at a lexical token boundary, so the string cannot be
    cut in half - but the operand may sit in one stream and its Tj in the next.  That is the case
    worth testing: the redaction callback tracks byte positions within one decoded stream, and here
    a single show operation spans two.
    """
    first = b"BT\n/F1 12 Tf\n20 100 Td\n(alpha bravo) Tj\n20 -20 Td\n(charlie delta)\n"
    second = b"Tj\nET\n"
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents [4 0 R 6 0 R] "
            b"/Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(first),
        5: helvetica(),
        6: stream_obj(second),
    }
    return build(objs)


def rotated_page():
    """/Rotate 90, so page space and text space disagree about which way is up."""
    return simple_page(b"BT\n/F1 12 Tf\n20 100 Td\n(alpha bravo charlie) Tj\nET\n",
                       page_extra=b"/Rotate 90 ")


def tight_leading():
    """Three lines 8pt apart in a 12pt font, so adjacent lines' glyph bounds overlap vertically.

    This is the case where flagging on any intersection can over-reach: a redaction box sized to a
    word on the middle line also touches the glyphs above and below it.  The fixture exists to
    measure that, not because tight leading is itself unusual.
    """
    return simple_page(b"BT\n/F1 12 Tf\n8 TL\n20 150 Td\n"
                       b"(above line text) Tj\nT*\n"
                       b"(middle bravo word) Tj\nT*\n"
                       b"(below line text) Tj\n"
                       b"ET\n")


def inline_image():
    """A page carrying an inline image (BI/ID/EI) alongside text.

    The image is emitted by the redaction callback rather than copied by the generic token path, so
    it is the case where a per-annotation loop used to write one copy of the image per annotation.
    Two redactable words give two annotations, which is all it takes.
    """
    # 4x4 8-bit greyscale, no filter: 16 bytes of raw samples.
    samples = bytes(range(0, 256, 16))
    image = (b"q 90 0 0 30 150 30 cm\n"
             b"BI /W 4 /H 4 /CS /G /BPC 8 ID " + samples + b"\nEI\nQ\n")
    # "over" sits inside the image's area so its redaction intersects the image; "alpha" on the
    # line above does not.  The order matters: the non-intersecting annotation has to be processed
    # first, which is why "alpha" is the higher of the two on the page.
    return simple_page(b"BT\n/F1 12 Tf\n20 150 Td\n(alpha bravo charlie) Tj\n"
                       b"1 0 0 1 160 40 Tm\n(over) Tj\nET\n" + image)


def rotated_image():
    """An 8x8 image XObject in four coloured quadrants, placed with a 90 degree rotation.

    cm [0 60 -60 0 200 100] turns the image a quarter turn, so a mapping taken from the placement's
    axis-aligned bounding box - which is square, and says nothing about which way the image faces -
    burns the wrong quadrant.  The raster is 8x8 rather than 2x2 so a redaction covering a quadrant
    lands on whole pixels: with antialiasing off, as a redaction needs, a sub-pixel fill covers no
    pixel centre at all and changes nothing.

    Quadrants, in raster order: red top-left, green top-right, blue bottom-left, white bottom-right.
    """
    rows = []
    for y in range(8):
        for x in range(8):
            if y < 4:
                rows.extend([255, 0, 0] if x < 4 else [0, 255, 0])
            else:
                rows.extend([0, 0, 255] if x < 4 else [255, 255, 255])
    samples = bytes(rows)
    image = (b"<< /Type /XObject /Subtype /Image /Width 8 /Height 8 /ColorSpace /DeviceRGB "
             b"/BitsPerComponent 8 /Length %d >>\nstream\n" % len(samples) + samples + b"\nendstream")
    return simple_page(b"q 0 60 -60 0 200 100 cm /Im0 Do Q\n",
                       extra_resources=b"/XObject << /Im0 6 0 R >>",
                       extra_objs={6: image})


def positionless_text():
    """A document whose redacted word also lives everywhere a rectangle cannot reach.

    "bravo" appears in the page content, in a bookmark title, in a comment's /Contents, and in the
    document title and keywords.  Burning the page removes exactly one of those five.
    """
    content = b"BT\n/F1 12 Tf\n20 100 Td\n(alpha bravo charlie) Tj\nET\n"
    objs = {
        1: (b"<< /Type /Catalog /Pages 2 0 R /Outlines 7 0 R >>"),
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Annots [9 0 R] "
            b"/Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(content),
        5: helvetica(),
        # outline: one root, one child whose title repeats the redacted word
        7: b"<< /Type /Outlines /First 8 0 R /Last 8 0 R /Count 1 >>",
        8: (b"<< /Title (bravo section) /Parent 7 0 R /Dest [3 0 R /Fit] >>"),
        # a comment whose text repeats it too
        9: (b"<< /Type /Annot /Subtype /Text /Rect [250 150 270 170] "
            b"/Contents (a note about bravo) /T (reviewer) >>"),
        10: b"<< /Title (bravo report) /Author (nobody) /Keywords (alpha, bravo) >>",
    }
    out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objs[num] + b"\nendobj\n"
    xref = len(out)
    top = max(objs) + 1
    out += b"xref\n0 %d\n" % top
    out += b"0000000000 65535 f \n"
    for num in range(1, top):
        if num in offsets:
            out += b"%010d 00000 n \n" % offsets[num]
        else:
            out += b"0000000000 65535 f \n"
    out += (b"trailer\n<< /Size %d /Root 1 0 R /Info 10 0 R >>\nstartxref\n%d\n%%%%EOF\n"
            % (top, xref))
    return bytes(out)


def form_and_destinations():
    """A text field holding the redacted word, and a named destination named after it.

    The field carries it twice, in /V and in /DV, because a reset would otherwise put it back.  The
    destination is referenced from a link annotation and from a bookmark, which is what makes
    renaming it more than a one-line change: the name is quoted by everything that jumps to it.
    """
    content = b"BT\n/F1 12 Tf\n20 100 Td\n(alpha bravo charlie) Tj\nET\n"
    objs = {
        1: (b"<< /Type /Catalog /Pages 2 0 R /Outlines 7 0 R /Names 11 0 R "
            b"/AcroForm << /Fields [12 0 R] >> >>"),
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Annots [12 0 R 13 0 R] /Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(content),
        5: helvetica(),
        7: b"<< /Type /Outlines /First 8 0 R /Last 8 0 R /Count 1 >>",
        # a bookmark that jumps to the named destination
        8: b"<< /Title (go to section) /Parent 7 0 R /Dest (bravo dest) >>",
        # the name tree holding that destination
        11: b"<< /Dests 14 0 R >>",
        14: b"<< /Names [(bravo dest) [3 0 R /Fit]] >>",
        # a text field whose value and default value both carry the word
        12: (b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (notes) /Rect [20 20 200 40] "
             b"/V (contains bravo) /DV (contains bravo) /DA (/Helv 0 Tf 0 g) >>"),
        # a link annotation that jumps to the same destination
        13: (b"<< /Type /Annot /Subtype /Link /Rect [20 60 200 80] "
             b"/A << /S /GoTo /D (bravo dest) >> >>"),
    }
    out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objs[num] + b"\nendobj\n"
    xref = len(out)
    top = max(objs) + 1
    out += b"xref\n0 %d\n" % top + b"0000000000 65535 f \n"
    for num in range(1, top):
        out += (b"%010d 00000 n \n" % offsets[num]) if num in offsets else b"0000000000 65535 f \n"
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (top, xref)
    return bytes(out)


def hidden_copies():
    """The places a redacted word hides that neither a rectangle nor /Info reaches.

    XMP metadata repeating the title, a comment carrying the word in /Contents, in its rich text
    /RC and in its author /T, and a page thumbnail - a picture of the page as it was, which cannot
    be redacted and can only be dropped.
    """
    content = b"BT\n/F1 12 Tf\n20 100 Td\n(alpha bravo charlie) Tj\nET\n"
    xmp = (b"<?xpacket begin='' id='W5M0MpCehiHzreSzNTczkc9d'?>\n"
           b"<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF "
           b"xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>"
           b"<rdf:Description xmlns:dc='http://purl.org/dc/elements/1.1/'>"
           b"<dc:title><rdf:Alt><rdf:li xml:lang='x-default'>bravo report</rdf:li></rdf:Alt></dc:title>"
           b"<dc:subject><rdf:Bag><rdf:li>bravo</rdf:li></rdf:Bag></dc:subject>"
           b"</rdf:Description></rdf:RDF></x:xmpmeta>\n<?xpacket end='w'?>")
    # a 2x2 greyscale thumbnail; content is irrelevant, its presence is the point
    thumb_samples = bytes([0, 64, 128, 255])
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R /Metadata 11 0 R /Names 13 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Annots [9 0 R] /Thumb 12 0 R "
            b"/Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(content),
        5: helvetica(),
        9: (b"<< /Type /Annot /Subtype /Text /Rect [250 150 270 170] "
            b"/Contents (a note about bravo) /RC (<body>rich bravo</body>) /T (bravo reviewer) >>"),
        # an attached file: nothing here can be masked, so a redaction can only drop it
        13: b"<< /EmbeddedFiles 14 0 R >>",
        14: b"<< /Names [(bravo source.txt) 15 0 R] >>",
        15: (b"<< /Type /Filespec /F (bravo source.txt) /Desc (notes about bravo) "
             b"/EF << /F 16 0 R >> >>"),
        16: (b"<< /Type /EmbeddedFile /Length 22 >>\nstream\n"
             b"bravo appears here too\nendstream"),
        11: (b"<< /Type /Metadata /Subtype /XML /Length %d >>\nstream\n" % len(xmp)
             + xmp + b"\nendstream"),
        12: (b"<< /Type /XObject /Subtype /Image /Width 2 /Height 2 /ColorSpace /DeviceGray "
             b"/BitsPerComponent 8 /Length %d >>\nstream\n" % len(thumb_samples)
             + thumb_samples + b"\nendstream"),
    }
    out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objs[num] + b"\nendobj\n"
    xref = len(out)
    top = max(objs) + 1
    out += b"xref\n0 %d\n" % top + b"0000000000 65535 f \n"
    for num in range(1, top):
        out += (b"%010d 00000 n \n" % offsets[num]) if num in offsets else b"0000000000 65535 f \n"
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (top, xref)
    return bytes(out)


def annotation_appearance():
    """A FreeText annotation whose appearance stream draws text under a redaction.

    An appearance stream is drawn on the page but is not part of the page's content, and its text
    never enters the page's text either - so search cannot find it and a redaction driven by search
    will not have covered it.  Only descending into the stream by geometry reaches it.

    The annotation sits at 20,140 to 200,180 and its appearance draws "secret annotation text"
    there; the page itself says something else entirely, so a leak is unambiguous.
    """
    ap = b"BT\n/F1 12 Tf\n5 10 Td\n(secret annotation text) Tj\nET\n"
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Annots [7 0 R] /Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(b"BT\n/F1 12 Tf\n20 60 Td\n(page says alpha) Tj\nET\n"),
        5: helvetica(),
        7: (b"<< /Type /Annot /Subtype /FreeText /Rect [20 140 200 180] /F 4 "
            b"/Contents (comment) /AP << /N 8 0 R >> >>"),
        8: (b"<< /Type /XObject /Subtype /Form /BBox [0 0 180 40] "
            b"/Resources << /Font << /F1 5 0 R >> >> /Length %d >>\nstream\n" % len(ap)
            + ap + b"\nendstream"),
    }
    out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objs[num] + b"\nendobj\n"
    xref = len(out)
    top = max(objs) + 1
    out += b"xref\n0 %d\n" % top + b"0000000000 65535 f \n"
    for num in range(1, top):
        out += (b"%010d 00000 n \n" % offsets[num]) if num in offsets else b"0000000000 65535 f \n"
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (top, xref)
    return bytes(out)


def hidden_layer():
    """A form on an optional-content layer that is OFF by default.

    The parser stops at the visibility check and never descends into the form, so a redaction over
    where the form draws used to leave its text sitting in the file - hidden by a flag anyone can
    turn back on, and visible to text extraction regardless.

    The form's /OC is a membership dictionary rather than the group directly.  Both are legal, but
    only the OCMD path actually consults the group's off state in ICEpdf today - see
    OptionalContent.isVisible(OptionalContentGroup), which reports any declared group as visible -
    so a fixture pointing /OC straight at the OCG is painted, and tests nothing.
    """
    form_content = b"BT\n/F1 12 Tf\n0 0 Td\n(hidden layer secret) Tj\nET\n"
    form = (b"<< /Type /XObject /Subtype /Form /FormType 1 /BBox [0 0 300 50] "
            b"/Matrix [1 0 0 1 20 140] /OC 8 0 R /Resources << /Font << /F1 5 0 R >> >> " +
            b"/Length %d >>\nstream\n" % len(form_content) + form_content + b"\nendstream")
    ocg = b"<< /Type /OCG /Name (hidden layer) >>"
    ocmd = b"<< /Type /OCMD /OCGs [7 0 R] /P /AllOn >>"
    content = b"BT\n/F1 12 Tf\n20 40 Td\n(page says alpha) Tj\nET\n/Fm0 Do\n"
    return build({
        1: (b"<< /Type /Catalog /Pages 2 0 R /OCProperties << /OCGs [7 0 R] "
            b"/D << /OFF [7 0 R] >> >> >>"),
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Resources << /Font << /F1 5 0 R >> /XObject << /Fm0 6 0 R >> >> >>"),
        4: stream_obj(content),
        5: helvetica(),
        6: form,
        7: ocg,
        8: ocmd,
    })


def tagged_text():
    """A tagged page whose structure tree repeats the page's words.

    /Alt and /ActualText exist so a reader can say or copy something other than the glyphs, which
    means burning the glyphs off the page leaves the sentence sitting in the structure tree for
    assistive technology and "copy text" to find.  /E (an abbreviation's expansion) and /T (the
    element's title) carry it too.
    """
    # The span also carries /ActualText inline in the content stream, which is the other place a
    # tagged PDF keeps a second copy of its words.
    content = (b"/Span << /ActualText (bravo) >> BDC\n"
               b"BT\n/F1 12 Tf\n20 150 Td\n(alpha bravo charlie) Tj\nET\nEMC\n")
    return build({
        1: b"<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(content),
        5: helvetica(),
        6: b"<< /Type /StructTreeRoot /K [7 0 R] >>",
        7: (b"<< /Type /StructElem /S /P /P 6 0 R /T (bravo section) "
            b"/Alt (a paragraph about bravo) /K [8 0 R] >>"),
        8: (b"<< /Type /StructElem /S /Span /P 7 0 R /ActualText (bravo) "
            b"/E (bravo expanded) /K 0 >>"),
    })


def stencil_mask():
    """An /ImageMask true stencil, drawn in red.

    A stencil carries no colour at all: each sample selects "paint the current fill colour here" or
    "leave this pixel alone", and which sample means which is decided by /Decode.  So there is no
    black in the image to overwrite, and a redaction that fills black pixels into the decoded raster
    is relying on an encoder to turn them back into the right samples.

    Top half of the image paints, bottom half does not, so a redaction can be checked against a
    region whose two halves started out different.  The fill colour is red rather than black, so
    anything that quietly assumes black shows up.
    """
    # 8x8, one byte per row.  0 bits paint under the default /Decode [0 1].
    rows = bytes([0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF])
    image = (b"<< /Type /XObject /Subtype /Image /Width 8 /Height 8 /ImageMask true "
             b"/BitsPerComponent 1 /Decode [0 1] /Length %d >>\nstream\n" % len(rows)
             + rows + b"\nendstream")
    content = (b"q 1 0 0 rg\n100 0 0 50 20 100 cm\n/Im0 Do\nQ\n"
               b"BT\n/F1 12 Tf\n20 40 Td\n(page says alpha) Tj\nET\n")
    return simple_page(content,
                       extra_resources=b"/XObject << /Im0 6 0 R >>",
                       extra_objs={6: image})


def image_drawn_twice():
    """One image XObject drawn at two positions.

    An image XObject is shared - a single object serves every placement of it, anywhere in the
    document - so where it is drawn belongs to the Do, not to the image.  A redaction over the second
    placement has to be tested against the second placement's position.
    """
    # 2x2 RGB, four distinct pixels so a burn is obvious.
    pixels = bytes([255, 0, 0, 0, 255, 0, 0, 0, 255, 255, 255, 0])
    image = (b"<< /Type /XObject /Subtype /Image /Width 2 /Height 2 /ColorSpace /DeviceRGB "
             b"/BitsPerComponent 8 /Length %d >>\nstream\n" % len(pixels) + pixels + b"\nendstream")
    content = (b"q 60 0 0 40 20 140 cm /Im0 Do Q\n"
               b"q 60 0 0 40 20 40 cm /Im0 Do Q\n")
    return simple_page(content,
                       extra_resources=b"/XObject << /Im0 6 0 R >>",
                       extra_objs={6: image})


def shared_content_alike():
    """Two pages pointing at one content stream, drawing it the same way.

    Redacting either page burns the shared stream, so both are redacted.  That is intended - they
    draw the same content, and the same content is the same disclosure - so nothing is reported.
    """
    return _shared_content(second_page_extra=b"")


def shared_content_rotated():
    """The same sharing, but the second page carries /Rotate 90.

    Same operators, different orientation, so a rectangle covering a word on one page covers
    something else on the other.  The redaction still propagates, and the report says so.
    """
    return _shared_content(second_page_extra=b"/Rotate 90 ")


def _shared_content(second_page_extra):
    content = b"BT\n/F1 12 Tf\n20 150 Td\n(alpha bravo charlie) Tj\nET\n"
    return build({
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R 7 0 R] /Count 2 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(content),
        5: helvetica(),
        # Second page, same /Contents object and the same resources.
        7: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Resources << /Font << /F1 5 0 R >> >> " + second_page_extra + b">>"),
    })


def flate_image():
    """The same 2x2 image as image_drawn_twice, but arriving Flate-compressed.

    The burn re-encodes whatever it touches, and the raster encoder writes Flate - which this already
    is.  So this one is burned without its filter changing, and is the control for the re-encode
    count.
    """
    import zlib
    pixels = zlib.compress(bytes([255, 0, 0, 0, 255, 0, 0, 0, 255, 255, 255, 0]), 9)
    image = (b"<< /Type /XObject /Subtype /Image /Width 2 /Height 2 /ColorSpace /DeviceRGB "
             b"/BitsPerComponent 8 /Filter /FlateDecode /Length %d >>\nstream\n" % len(pixels)
             + pixels + b"\nendstream")
    return simple_page(b"q 60 0 0 40 20 140 cm /Im0 Do Q\n",
                       extra_resources=b"/XObject << /Im0 6 0 R >>",
                       extra_objs={6: image})


def soft_masked_image():
    """An image with an /SMask, the greyscale image that supplies its alpha.

    White is opaque and black is transparent, so the soft mask decides whether the block burned into
    the base image can be seen at all - and, even once the pixels are replaced, its alpha channel
    still carries the shape of what was removed.  The mask here is 4x1 running dark to light, so a
    redaction over part of it can be checked against samples that started at different values.
    """
    # 4x1 base, four distinct colours.
    pixels = bytes([255, 0, 0,  0, 255, 0,  0, 0, 255,  255, 255, 0])
    smask = bytes([0, 80, 160, 255])
    image = (b"<< /Type /XObject /Subtype /Image /Width 4 /Height 1 /ColorSpace /DeviceRGB "
             b"/BitsPerComponent 8 /SMask 7 0 R /Length %d >>\nstream\n" % len(pixels)
             + pixels + b"\nendstream")
    mask = (b"<< /Type /XObject /Subtype /Image /Width 4 /Height 1 /ColorSpace /DeviceGray "
            b"/BitsPerComponent 8 /Length %d >>\nstream\n" % len(smask) + smask + b"\nendstream")
    return simple_page(b"q 80 0 0 40 20 140 cm /Im0 Do Q\n",
                       extra_resources=b"/XObject << /Im0 6 0 R >>",
                       extra_objs={6: image, 7: mask})


def colour_key_masked_image():
    """An image whose /Mask is a colour-key range covering black.

    A colour-key mask names colours to drop rather than paint.  The redaction colour is black by
    default, and this mask drops near-black, so the block burned into the image is dropped with it:
    the pixels are gone but the page shows through, which looks like a redaction that never ran.
    """
    pixels = bytes([255, 0, 0,  0, 255, 0,  0, 0, 255,  255, 255, 0])
    image = (b"<< /Type /XObject /Subtype /Image /Width 4 /Height 1 /ColorSpace /DeviceRGB "
             b"/BitsPerComponent 8 /Mask [0 20 0 20 0 20] /Length %d >>\nstream\n" % len(pixels)
             + pixels + b"\nendstream")
    return simple_page(b"q 80 0 0 40 20 140 cm /Im0 Do Q\n",
                       extra_resources=b"/XObject << /Im0 6 0 R >>",
                       extra_objs={6: image})


def gray_image():
    """A DeviceGray image XObject, 4x1, running dark to light.

    Burning it used to convert it to DeviceRGB - three bytes a pixel for a one-channel image, which
    on a scanned page is most of the file.  The samples are distinct so a redaction over part of it
    can be checked against values that started out different.
    """
    samples = bytes([0, 80, 160, 255])
    image = (b"<< /Type /XObject /Subtype /Image /Width 4 /Height 1 /ColorSpace /DeviceGray "
             b"/BitsPerComponent 8 /Length %d >>\nstream\n" % len(samples) + samples + b"\nendstream")
    return simple_page(b"q 80 0 0 40 20 140 cm /Im0 Do Q\n",
                       extra_resources=b"/XObject << /Im0 6 0 R >>",
                       extra_objs={6: image})


FIXTURES = {
    "gray_image.pdf": gray_image,
    "colour_key_masked_image.pdf": colour_key_masked_image,
    "soft_masked_image.pdf": soft_masked_image,
    "flate_image.pdf": flate_image,
    "shared_content_alike.pdf": shared_content_alike,
    "shared_content_rotated.pdf": shared_content_rotated,
    "image_drawn_twice.pdf": image_drawn_twice,
    "stencil_mask.pdf": stencil_mask,
    "tagged_text.pdf": tagged_text,
    "hidden_layer.pdf": hidden_layer,
    "annotation_appearance.pdf": annotation_appearance,
    "hidden_copies.pdf": hidden_copies,
    "form_and_destinations.pdf": form_and_destinations,
    "positionless_text.pdf": positionless_text,
    "rotated_image.pdf": rotated_image,
    "inline_image.pdf": inline_image,
    "tight_leading.pdf": tight_leading,
    "simple_tj.pdf": simple_tj,
    "tj_array.pdf": tj_array,
    "quote_operators.pdf": quote_operators,
    "text_state.pdf": text_state,
    "form_xobject.pdf": form_xobject,
    "form_drawn_twice.pdf": form_drawn_twice,
    "multi_stream.pdf": multi_stream,
    "rotated_page.pdf": rotated_page,
}

if __name__ == "__main__":
    for filename, factory in sorted(FIXTURES.items()):
        write(filename, factory())
