#!/usr/bin/env python3
"""Generate the CCITT fax fixtures the decoder tests run against.

ICEpdf carries its own CCITT decoder, reached only when the TwelveMonkeys one throws, so nothing in
the corpus exercises it - a fax renders through TwelveMonkeys and the fallback is never asked.  That
left the whole of CCITTFaxDecoder untested, which is a poor state for the code that runs precisely
when a file is already unusual.

Each fixture is a pair: the raw CCITT bits as they appear in a PDF stream, and the picture they
decode to.  The bits come from libtiff by way of Pillow, and the picture is written separately as a
PBM, so the test compares one implementation against another rather than against itself.  A test
built on data this decoder produced would pass whatever the decoder did.

PBM (P4) is the oracle format on purpose: its raster is one bit per pixel, rows padded to a byte,
1 meaning black - byte for byte what CCITTFaxDecoder is supposed to write before BlackIs1 is
applied.  So the comparison needs no image library and no conversion step to get wrong.

Run from this directory:  python3 make_ccitt_fixtures.py
"""

import io

from PIL import Image
from PIL.TiffImagePlugin import ImageFileDirectory_v2

# T4Options bits: 1 = 2-D encoding, 4 = fill bits, meaning zero bits are inserted before an EOL so
# that the EOL ends on a byte boundary.
#
# Fill is NOT the same thing as a PDF's /EncodedByteAlign, though both end up starting lines on byte
# boundaries.  Fill is part of the T.4 bitstream and is consumed by reading past it to the EOL;
# /EncodedByteAlign is an instruction to the filter to skip to the next boundary itself, and a stream
# written for it generally carries no EOL at all.  libtiff writes the first and has no way to write
# the second, so these fixtures cover fill only - decode them with setAlign(false).
ENCODINGS = [
    ("g4", "group4", None),      # K < 0   - T.6
    ("g3_1d", "group3", None),   # K = 0   - T.4 one dimensional
    ("g3_2d", "group3", 1),      # K > 0   - T.4 mixed, 2-D lines allowed
    ("g3_1d_fill", "group3", 4),
    ("g3_2d_fill", "group3", 5),
]


def bitmap(width, height, black):
    """A bilevel image; black(x, y) says whether that pixel is black.

    Mode "1" is 0 for black, so the predicate is inverted on the way in.
    """
    image = Image.new("1", (width, height), 1)
    for y in range(height):
        for x in range(width):
            if black(x, y):
                image.putpixel((x, y), 0)
    return image


def encode(image, compression, t4options):
    """The raw CCITT bytes libtiff produces for this image, lifted out of the TIFF it wraps them in."""
    info = None
    if t4options is not None:
        info = ImageFileDirectory_v2()
        info[292] = t4options

    buffer = io.BytesIO()
    image.save(buffer, format="TIFF", compression=compression,
               **({"tiffinfo": info} if info else {}))
    buffer.seek(0)

    tiff = Image.open(buffer)
    raw = buffer.getvalue()
    # One strip per image is what we ask for below; join anyway so a multi-strip write cannot
    # silently truncate a fixture.
    data = b"".join(raw[offset:offset + count]
                    for offset, count in zip(tiff.tag_v2[273], tiff.tag_v2[279]))

    # libtiff has to be able to read back what it wrote, or the fixture is nonsense before the
    # decoder ever sees it.  Mode "1" reads back as 0/255 and compares as 0/1, hence the bool().
    original = [bool(p) for p in image.getdata()]
    reread = [bool(p) for p in tiff.getdata()]
    assert original == reread, "libtiff did not round trip its own %s output" % compression
    return data


def write(name, image):
    """Writes the picture once and the encoded bits once per encoding."""
    pbm = io.BytesIO()
    image.save(pbm, format="PPM")
    with open("%s.pbm" % name, "wb") as handle:
        handle.write(pbm.getvalue())

    sizes = []
    for suffix, compression, t4options in ENCODINGS:
        data = encode(image, compression, t4options)
        with open("%s.%s.ccitt" % (name, suffix), "wb") as handle:
            handle.write(data)
        sizes.append("%s=%d" % (suffix, len(data)))
    print("%-12s %4dx%-4d  %s" % (name, image.width, image.height, "  ".join(sizes)))


if __name__ == "__main__":
    # A block pattern: ordinary short runs, the everyday case.
    write("checker", bitmap(64, 32, lambda x, y: ((x // 4) + (y // 4)) % 2 == 0))

    # Alternating single pixels: the shortest runs there are, and the worst case for a coder that
    # mishandles a run of length one.
    write("stripes", bitmap(64, 32, lambda x, y: x % 2 == 0))

    # Rows that repeat exactly, which is what 2-D coding is for: every line after the first is a
    # vertical copy of the one above.
    write("vertical", bitmap(64, 24, lambda x, y: 16 <= x < 48))

    # Nothing at all, and everything: the two degenerate images, where a run covers the whole row.
    write("blank", bitmap(64, 16, lambda x, y: False))
    write("solid", bitmap(64, 16, lambda x, y: True))

    # One black pixel in a field of white, so a decoder that loses a single short run fails rather
    # than rounding it away.
    write("dot", bitmap(64, 16, lambda x, y: x == 10 and y == 5))

    # A width that is not a whole number of bytes and an odd height, so the row padding is wrong in
    # the output if the decoder packs rows by anything other than the column count.
    write("narrow", bitmap(61, 13, lambda x, y: (x + y) % 3 == 0))

    # Full fax width with runs long enough to need makeup codes, which are a separate code table
    # from the terminating codes and are where a decoder tends to go wrong.
    write("fax1728", bitmap(1728, 8, lambda x, y: 100 <= x < 1500))
