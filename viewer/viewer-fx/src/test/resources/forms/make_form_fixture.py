#!/usr/bin/env python3
# Copyright 2026 Patrick Corless
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Generate all_fields.pdf: one AcroForm with one of every field type the JavaFX viewer fills.

Project-authored (no third-party content), in the hand-built style of viewer-awt's
make_search_fixtures.py: plain objects, an explicit xref, no dependency.

Fields (fully-qualified names):
  name        single-line text, value "Ada", default ""
  notes       multi-line text
  secret      password
  code        text with /MaxLen 5, value "AB"
  agree       check box (on state /Yes), off, default off
  color       radio group Red / Green / Blue, value Red, default Red, NoToggleToOff
  pair        radio group with RadiosInUnison: two kids share the /A on-state, one is /B
  country     combo box Canada / France / Japan, value France, default Canada
  city        editable combo box
  fruit       list box Apple / Banana / Cherry, value Banana
  toppings    multi-select list box, indexes 0 and 2
  reset       push button, /A ResetForm
  submit      push button, /A SubmitForm to http://example.invalid/submit

Run from this directory:  python3 make_form_fixture.py
"""


def build(objs, root=1):
    out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objs[num] + b"\nendobj\n"
    xref = len(out)
    size = max(objs) + 1
    out += b"xref\n0 %d\n0000000000 65535 f \n" % size
    for num in range(1, size):
        out += b"%010d 00000 n \n" % offsets[num]
    out += b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (size, root, xref)
    return bytes(out)


def stream(dictionary, data):
    return b"<< %s /Length %d >>\nstream\n%s\nendstream" % (dictionary, len(data), data)


def form_xobject(width, height, data):
    return stream(b"/Type /XObject /Subtype /Form /BBox [0 0 %d %d] /Resources << /Font << /Helv 5 0 R >> >>"
                  % (width, height), data)


objs = {}
num = [10]


def new(obj):
    n = num[0]
    num[0] += 1
    objs[n] = obj
    return n


PAGE = 4
annots = []
fields = []


def text_ap(width, height, value):
    return new(form_xobject(width, height, b"/Tx BMC BT /Helv 11 Tf 0 g 2 %d Td (%s) Tj ET EMC"
                            % (height // 2 - 4, value)))


def text_field(name, rect, value=b"", flags=0, extra=b""):
    x0, y0, x1, y1 = rect
    ap = text_ap(x1 - x0, y1 - y0, value)
    n = new(b"<< /Type /Annot /Subtype /Widget /FT /Tx /T (%s) /V (%s) /DV () /Ff %d /DA (/Helv 11 Tf 0 g) "
            b"/Rect [%d %d %d %d] /F 4 /P %d 0 R /AP << /N %d 0 R >> /MK << /BC [0.5 0.5 0.5] >> %s >>"
            % (name, value, flags, x0, y0, x1, y1, PAGE, ap, extra))
    annots.append(n)
    fields.append(n)


ON = b"0 0 1 rg 3 3 %d %d re f"


def button_states(size, on_name):
    on = new(form_xobject(size, size, ON % (size - 6, size - 6)))
    off = new(form_xobject(size, size, b"0.6 g 0.5 0.5 %d %d re S" % (size - 1, size - 1)))
    return b"/AP << /N << /%s %d 0 R /Off %d 0 R >> >>" % (on_name, on, off)


def push_button(name, rect, action, label):
    x0, y0, x1, y1 = rect
    ap = new(form_xobject(x1 - x0, y1 - y0, b"0.85 g 0 0 %d %d re f BT /Helv 10 Tf 0 g 6 6 Td (%s) Tj ET"
                          % (x1 - x0, y1 - y0, label)))
    n = new(b"<< /Type /Annot /Subtype /Widget /FT /Btn /Ff 65536 /T (%s) /Rect [%d %d %d %d] /F 4 /P %d 0 R "
            b"/AP << /N %d 0 R >> /A %s >>" % (name, x0, y0, x1, y1, PAGE, ap, action))
    annots.append(n)
    fields.append(n)


# -- text fields
text_field(b"name", (150, 720, 400, 742), b"Ada")
text_field(b"notes", (150, 640, 400, 710), b"line one", flags=4096)
text_field(b"secret", (150, 610, 400, 630), flags=8192)
text_field(b"code", (150, 580, 250, 600), b"AB", extra=b"/MaxLen 5")

# -- check box
agree = new(b"<< /Type /Annot /Subtype /Widget /FT /Btn /T (agree) /V /Off /DV /Off /AS /Off "
            b"/Rect [150 545 166 561] /F 4 /P %d 0 R %s >>" % (PAGE, button_states(16, b"Yes")))
annots.append(agree)
fields.append(agree)

# -- radio group: NoToggleToOff (16384) + Radio (32768)
color = new(b"null")  # placeholder: the kids need the parent's number before it is written
kids = []
for i, colour in enumerate([b"Red", b"Green", b"Blue"]):
    x = 150 + i * 40
    state = colour if colour == b"Red" else b"Off"
    k = new(b"<< /Type /Annot /Subtype /Widget /Parent %d 0 R /AS /%s /Rect [%d 505 %d 521] /F 4 /P %d 0 R %s >>"
            % (color, state, x, x + 16, PAGE, button_states(16, colour)))
    kids.append(k)
    annots.append(k)
objs[color] = (b"<< /FT /Btn /T (color) /Ff 49152 /V /Red /DV /Red /Kids [%s] >>"
               % b" ".join(b"%d 0 R" % k for k in kids))
fields.append(color)

# -- radio group in unison: RadiosInUnison (1 << 25) + Radio + NoToggleToOff
pair = new(b"null")
kids = []
for i, state_name in enumerate([b"A", b"A", b"B"]):
    x = 150 + i * 40
    k = new(b"<< /Type /Annot /Subtype /Widget /Parent %d 0 R /AS /Off /Rect [%d 470 %d 486] /F 4 /P %d 0 R %s >>"
            % (pair, x, x + 16, PAGE, button_states(16, state_name)))
    kids.append(k)
    annots.append(k)
objs[pair] = (b"<< /FT /Btn /T (pair) /Ff %d /V /Off /DV /Off /Kids [%s] >>"
              % ((1 << 25) | 49152, b" ".join(b"%d 0 R" % k for k in kids)))
fields.append(pair)


def choice(name, rect, flags, options, value, extra=b"", array_value=False):
    x0, y0, x1, y1 = rect
    ap = text_ap(x1 - x0, y1 - y0, b"" if array_value else value)
    opts = b" ".join(b"(%s)" % o for o in options)
    v = value if array_value else b"(%s)" % value
    n = new(b"<< /Type /Annot /Subtype /Widget /FT /Ch /T (%s) /Ff %d /Opt [%s] /V %s /DA (/Helv 11 Tf 0 g) "
            b"/Rect [%d %d %d %d] /F 4 /P %d 0 R /AP << /N %d 0 R >> %s >>"
            % (name, flags, opts, v, x0, y0, x1, y1, PAGE, ap, extra))
    annots.append(n)
    fields.append(n)


choice(b"country", (150, 430, 300, 450), 131072, [b"Canada", b"France", b"Japan"], b"France", b"/DV (Canada)")
choice(b"city", (150, 400, 300, 420), 131072 | 262144, [b"Calgary", b"Lyon", b"Osaka"], b"Lyon")
choice(b"fruit", (150, 330, 300, 390), 0, [b"Apple", b"Banana", b"Cherry"], b"Banana")
choice(b"toppings", (150, 260, 300, 320), 2097152, [b"Apple", b"Banana", b"Cherry"], b"[(Apple) (Cherry)]",
       b"/I [0 2]", array_value=True)

push_button(b"reset", (150, 220, 230, 244), b"<< /S /ResetForm >>", b"Reset")
push_button(b"submit", (250, 220, 330, 244), b"<< /S /SubmitForm /F (http://example.invalid/submit) >>", b"Submit")

labels = [(742, b"Name"), (700, b"Notes"), (620, b"Secret"), (590, b"Code"), (553, b"Agree"), (513, b"Colour"),
          (478, b"Pair"), (440, b"Country"), (410, b"City"), (380, b"Fruit"), (310, b"Toppings")]
content = b"BT /Helv 11 Tf " + b" ".join(b"1 0 0 1 60 %d Tm (%s) Tj" % (y - 14, t) for y, t in labels) + b" ET"

objs[1] = b"<< /Type /Catalog /Pages 2 0 R /AcroForm 3 0 R >>"
objs[2] = b"<< /Type /Pages /Kids [4 0 R] /Count 1 >>"
objs[3] = (b"<< /Fields [%s] /DR << /Font << /Helv 5 0 R >> >> /DA (/Helv 0 Tf 0 g) >>"
           % b" ".join(b"%d 0 R" % f for f in fields))
objs[4] = (b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /Helv 5 0 R >> >> "
           b"/Contents 6 0 R /Annots [%s] >>" % b" ".join(b"%d 0 R" % a for a in annots))
objs[5] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
objs[6] = stream(b"", content)
# fill the gaps between the fixed and allocated object numbers with nulls, so the xref is dense.
for n in range(1, max(objs) + 1):
    objs.setdefault(n, b"null")

with open("all_fields.pdf", "wb") as f:
    f.write(build(objs))
print("wrote all_fields.pdf")
