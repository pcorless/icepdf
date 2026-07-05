#!/usr/bin/env python3
# Build a minimal PDF that distinguishes isolated vs non-isolated group routing.
# Page: cyan background. Isolated transparency group draws a gray rect with
# /BM Multiply. Group ExtGState itself has NO effect (Normal, ca 1) so today's
# router sends it INLINE -> inner Multiply hits the cyan page (gray*cyan = teal).
# Correct isolated semantics (buffer) -> Multiply hits transparent backdrop ->
# plain gray over cyan.
import struct

objs = {}

objs[1] = b"<< /Type /Catalog /Pages 2 0 R >>"
objs[2] = b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>"
objs[3] = (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] "
           b"/Contents 4 0 R /Resources << "
           b"/ExtGState << /GS0 6 0 R >> "
           b"/XObject << /Fm0 5 0 R >> >> >>")

# Page content: cyan fill, then invoke the group under a no-effect Normal gs
# (GS0) so the form's captured ExtGState is non-null and routing is reached.
page_stream = (b"q\n"
               b"0 1 1 rg\n"          # DeviceRGB cyan
               b"0 0 200 200 re f\n"
               b"/GS0 gs\n"           # Normal, ca 1 -> no group effect
               b"/Fm0 Do\n"
               b"Q\n")
objs[4] = b"<< /Length %d >>\nstream\n" % len(page_stream) + page_stream + b"endstream"

# Isolated transparency-group form. Inner content multiplies a gray rect.
form_stream = (b"q\n"
               b"/GS1 gs\n"           # BM Multiply (inner)
               b"0.5 0.5 0.5 rg\n"    # gray
               b"50 50 100 100 re f\n"
               b"Q\n")
objs[5] = (b"<< /Type /XObject /Subtype /Form /FormType 1 "
           b"/BBox [50 50 150 150] "
           b"/Group << /Type /Group /S /Transparency /I true /CS /DeviceRGB >> "
           b"/Resources << /ExtGState << /GS1 7 0 R >> >> "
           b"/Length %d >>\nstream\n" % len(form_stream)) + form_stream + b"endstream"

objs[6] = b"<< /Type /ExtGState /BM /Normal /ca 1 /CA 1 >>"
objs[7] = b"<< /Type /ExtGState /BM /Multiply /ca 1 /CA 1 >>"

# Serialize with a correct xref table.
out = bytearray(b"%PDF-1.5\n%\xe2\xe3\xcf\xd3\n")
offsets = {}
for n in range(1, 8):
    offsets[n] = len(out)
    out += b"%d 0 obj\n" % n + objs[n] + b"\nendobj\n"

xref_off = len(out)
out += b"xref\n0 8\n"
out += b"0000000000 65535 f \n"
for n in range(1, 8):
    out += b"%010d 00000 n \n" % offsets[n]
out += b"trailer\n<< /Size 8 /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % xref_off

with open("/tmp/bench/iso_fixture.pdf", "wb") as f:
    f.write(out)
print("wrote /tmp/bench/iso_fixture.pdf (%d bytes)" % len(out))
