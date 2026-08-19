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


def write(name, data):
    with open(name, "wb") as handle:
        handle.write(data)
    print("wrote %-28s %5d bytes" % (name, len(data)))


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


FIXTURES = {
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
