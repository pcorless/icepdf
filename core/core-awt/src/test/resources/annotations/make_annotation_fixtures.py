#!/usr/bin/env python3
"""Generate the minimal PDFs the annotation state-manager tests run against.

Each fixture is the smallest file that forces the library to write something of its own just to
render the page - a missing appearance stream, an /AcroForm asking the reader to build the
appearances itself.  The tests then assert that work is recorded as a repair and not as a user
edit, which is the whole point of StateManager.repairing(...).

Same hand-built style as redaction/make_redaction_fixtures.py: no third-party dependency, explicit
uniform /Widths so glyph advances do not vary by host.

Run from this directory:  python3 make_annotation_fixtures.py
"""

WIDTH = 500  # uniform glyph width, in 1/1000 text space units


def build(objs, root=1, trailer_extra=b""):
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
        out += (b"%010d 00000 n \n" % offsets[num]) if num in offsets else b"0000000000 65535 f \n"
    out += (b"trailer\n<< /Size %d /Root %d 0 R " % (top, root) + trailer_extra +
            b">>\nstartxref\n%d\n%%%%EOF\n" % xref)
    return bytes(out)


def stream_obj(data, extra=b""):
    return b"<< /Length %d %s>>\nstream\n" % (len(data), extra) + data + b"\nendstream"


def helvetica(name=b"/Helvetica"):
    widths = b"[" + b" ".join(b"%d" % WIDTH for _ in range(32, 127)) + b"]"
    return (b"<< /Type /Font /Subtype /Type1 /BaseFont " + name +
            b" /FirstChar 32 /LastChar 126 /Widths " + widths +
            b" /Encoding /WinAnsiEncoding >>")


def write(name, data):
    with open(name, "wb") as handle:
        handle.write(data)
    print("wrote %-28s %5d bytes" % (name, len(data)))


# -- fixtures ------------------------------------------------------------------------------------

def missing_appearance():
    """A Square annotation with no /AP at all.

    Annotation.resetNullAppearanceStream() has to manufacture one before the annotation can be
    painted.  Nothing here was asked for by the user, so opening this file and closing it again
    must not offer to save.
    """
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Annots [6 0 R] /Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(b"BT\n/F1 12 Tf\n20 60 Td\n(page says alpha) Tj\nET\n"),
        5: helvetica(),
        # no /AP - this is the point of the fixture.
        6: (b"<< /Type /Annot /Subtype /Square /Rect [20 140 200 180] /F 4 "
            b"/C [1 0 0] /CA 1.0 >>"),
    }
    return build(objs)


def need_appearances():
    """An /AcroForm with /NeedAppearances true and one text field carrying a value but no /AP.

    /NeedAppearances is the file telling the reader to build the field appearances itself.
    AbstractWidgetAnnotation.init() does exactly that, and it is a repair: the user has not typed
    anything.
    """
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R /AcroForm 7 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Annots [6 0 R] /Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(b"BT\n/F1 12 Tf\n20 60 Td\n(form below) Tj\nET\n"),
        5: helvetica(),
        # no /AP - /NeedAppearances is what is meant to supply it.
        6: (b"<< /Type /Annot /Subtype /Widget /Rect [20 140 200 165] /F 4 "
            b"/FT /Tx /T (name) /V (from the file) /DA (/F1 12 Tf 0 g) >>"),
        7: (b"<< /Fields [6 0 R] /NeedAppearances true "
            b"/DR << /Font << /F1 5 0 R >> >> /DA (/F1 12 Tf 0 g) >>"),
    }
    return build(objs)


def text_field():
    """The same form, with an appearance stream already present and no /NeedAppearances.

    Opening this file gives the library no reason to write anything, so it is the control: any
    change in the state manager afterwards came from the test, not from rendering.
    """
    ap = b"/Tx BMC\nq\nBT\n/F1 12 Tf\n2 7 Td\n(from the file) Tj\nET\nQ\nEMC\n"
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R /AcroForm 7 0 R >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R "
            b"/Annots [6 0 R] /Resources << /Font << /F1 5 0 R >> >> >>"),
        4: stream_obj(b"BT\n/F1 12 Tf\n20 60 Td\n(form below) Tj\nET\n"),
        5: helvetica(),
        6: (b"<< /Type /Annot /Subtype /Widget /Rect [20 140 200 165] /F 4 "
            b"/FT /Tx /T (name) /V (from the file) /DA (/F1 12 Tf 0 g) /AP << /N 8 0 R >> >>"),
        7: (b"<< /Fields [6 0 R] /DR << /Font << /F1 5 0 R >> >> /DA (/F1 12 Tf 0 g) >>"),
        8: (b"<< /Type /XObject /Subtype /Form /BBox [0 0 180 25] "
            b"/Resources << /Font << /F1 5 0 R >> >> /Length %d >>\nstream\n" % len(ap)
            + ap + b"\nendstream"),
    }
    return build(objs)


FIXTURES = {
    "missing_appearance.pdf": missing_appearance,
    "need_appearances.pdf": need_appearances,
    "text_field.pdf": text_field,
}

if __name__ == "__main__":
    for filename, factory in sorted(FIXTURES.items()):
        write(filename, factory())
