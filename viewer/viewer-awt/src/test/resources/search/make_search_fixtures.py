#!/usr/bin/env python3
"""Generate the minimal PDF the non-text search tests run against.

The search controller looks in four places besides the page text: the outline, the named
destinations, the values typed into form fields, and the contents of comments.  The reference
addendum fixture already covers the first two, and has neither form fields nor markup annotations -
so the form and comment searches had nothing real to find.

This builds the smallest file that gives them something: two text fields carrying values, three
comments carrying text, and one comment carrying none.  That last one is not padding.  A stamp or a
plain highlight has no /Contents, the regex branch of the comment search used to pass it straight
to a matcher, and one such comment on a page threw the whole search.

Same hand-built style as core's redaction and annotation fixture scripts: no third-party
dependency, and an explicit uniform /Widths so glyph advances do not vary by host.

Run from this directory:  python3 make_search_fixtures.py
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
        out += (b"%010d 00000 n \n" % offsets[num]) if num in offsets else b"0000000000 65535 f \n"
    out += (b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (top, root, xref))
    return bytes(out)


def stream_obj(data, extra=b""):
    return b"<< /Length %d %s>>\nstream\n" % (len(data), extra) + data + b"\nendstream"


def helvetica():
    widths = b"[" + b" ".join(b"%d" % WIDTH for _ in range(32, 127)) + b"]"
    return (b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica"
            b" /FirstChar 32 /LastChar 126 /Widths " + widths +
            b" /Encoding /WinAnsiEncoding >>")


def text_field(number, name, value, rect):
    """A text widget annotation that is also its own form field, with a value to find."""
    return (b"<< /Type /Annot /Subtype /Widget /FT /Tx"
            b" /T (" + name + b") /V (" + value + b")"
            b" /Rect [" + rect + b"] /F 4 /DA (/Helv 12 Tf 0 g) >>")


def comment(number, contents, rect):
    """A sticky note carrying text, which is what the comment search reads."""
    body = (b"<< /Type /Annot /Subtype /Text /Rect [" + rect + b"]"
            b" /T (Reviewer) /F 4")
    if contents is not None:
        body += b" /Contents (" + contents + b")"
    return body + b" >>"


def searchable():
    """One page carrying form fields and comments, with text on it as well.

    The field values and comment texts are deliberately distinct words, so a test can say which of
    the four searches found a thing rather than only that something was found.
    """
    content = (b"BT /F1 12 Tf 72 720 Td (Printed page text mentions aardvark once.) Tj ET")
    objs = {
        1: b"<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [5 0 R 6 0 R] /DA (/Helv 0 Tf 0 g) >> >>",
        2: b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        3: (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]"
            b" /Resources << /Font << /F1 4 0 R >> >>"
            b" /Contents 7 0 R"
            b" /Annots [5 0 R 6 0 R 8 0 R 9 0 R 10 0 R] >>"),
        4: helvetica(),
        # form fields: one holding a word, one holding a phrase with a bracket in it, which a
        # plain search has to treat as text rather than as a pattern
        5: text_field(5, b"invoice", b"badger total 42", b"72 600 300 620"),
        6: text_field(6, b"address", b"14 Canal Street (rear)", b"72 560 300 580"),
        7: stream_obj(content),
        # comments: two with text, one with none at all
        8: comment(8, b"the capybara looks wrong here", b"400 700 420 720"),
        9: comment(9, b"check the badger figure", b"400 660 420 680"),
        10: comment(10, None, b"400 620 420 640"),
    }
    return build(objs)


def write(name, data):
    with open(name, "wb") as handle:
        handle.write(data)
    print("wrote %-28s %5d bytes" % (name, len(data)))


if __name__ == "__main__":
    write("form_fields_and_comments.pdf", searchable())
