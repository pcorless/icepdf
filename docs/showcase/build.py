"""Build the showcase PDF: a fictional expedition field report used for the site's screenshots and videos.

    python3 build.py            (needs pypdf and google-chrome; writes expedition-report.pdf next to this file)

1. Fill report.html (cover photo, melt chart, form-field boxes) and print it with headless Chrome.
2. With pypdf (BSD), add what HTML can't make: AcroForm fields over the drawn boxes, a signature field,
   and sticky notes and highlights from several authors in several colours (for the annotation board).

All names, phone numbers (555-01xx is reserved for fiction), permits and places are made up.
"""
import argparse, base64, html, json, os, re, subprocess, tempfile

from pypdf import PdfReader, PdfWriter
from pypdf.generic import (ArrayObject, BooleanObject, DecodedStreamObject, DictionaryObject, FloatObject,
                           NameObject, NumberObject, TextStringObject)

HERE = os.path.dirname(os.path.abspath(__file__))
CHROME = 'google-chrome'
PAGE_W_PT, PAGE_H_PT = 595.28, 841.89    # A4
MM = 72 / 25.4                          # mm -> pt
PX = 0.75                               # CSS px -> pt
PAGE_H_PX = 297 / 25.4 * 96             # one page in CSS px

# ---------------------------------------------------------------------------------------------- cover + chart

PLACEHOLDER_COVER = '''<svg viewBox="0 0 210 297" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
<defs>
 <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2b3f8f"/><stop offset=".55" stop-color="#8fb4e6"/><stop offset="1" stop-color="#dfe9f7"/></linearGradient>
 <linearGradient id="far" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#b7c8e4"/><stop offset="1" stop-color="#7e93bd"/></linearGradient>
 <linearGradient id="near" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f4f8ff"/><stop offset="1" stop-color="#b9cbe6"/></linearGradient>
 <linearGradient id="shade" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#5d74a8" stop-opacity=".0"/><stop offset="1" stop-color="#3b4f86" stop-opacity=".55"/></linearGradient>
</defs>
<rect width="210" height="297" fill="url(#sky)"/>
<path d="M0 175 L22 150 L38 162 L60 128 L78 146 L96 118 L118 140 L140 112 L162 136 L186 120 L210 142 V297 H0Z" fill="url(#far)"/>
<path d="M-5 215 L30 160 L48 176 L82 122 L104 150 L122 134 L150 170 L176 140 L215 190 V297 H-5Z" fill="url(#near)"/>
<path d="M82 122 L104 150 L96 168 L78 196 L70 170 Z M176 140 L215 190 L200 205 L182 178 Z" fill="url(#shade)"/>
<path d="M82 122 L92 135 L86 134 L80 142 L76 133 L70 136 Z M176 140 L186 152 L180 151 L175 158 L171 150 L166 153 Z" fill="#ffffff"/>
<path d="M-5 250 C40 232 90 238 130 226 C160 218 190 222 215 214 V297 H-5Z" fill="#eef3fb"/>
<path d="M-5 268 C50 252 110 262 215 244 V297 H-5Z" fill="#dfe8f6"/>
<text x="105" y="290" text-anchor="middle" font-size="4" fill="#5a6577" font-family="Noto Sans, sans-serif">placeholder: cover photo goes here</text>
</svg>'''


def cover_photo(path):
    if not path or not os.path.exists(path):
        return PLACEHOLDER_COVER
    mime = 'image/png' if path.lower().endswith('.png') else 'image/jpeg'
    data = base64.b64encode(open(path, 'rb').read()).decode()
    return f'<img src="data:{mime};base64,{data}" alt="">'


MELT_2026 = [12, 15, 18, 16, 20, 22, 24, 38, 41, 36, 25, 21, 19, 17]
MELT_2019 = [10, 11, 13, 12, 15, 16, 17, 18, 20, 19, 17, 15, 14, 12]


def melt_chart():
    w, h, l, r, t, b = 600, 160, 40, 12, 10, 26
    x = lambda i: l + i * (w - l - r) / (len(MELT_2026) - 1)
    y = lambda v: t + (1 - v / 45) * (h - t - b)
    line = lambda vs: ' '.join(f'{x(i):.1f},{y(v):.1f}' for i, v in enumerate(vs))
    area = f'M{x(0):.1f},{y(0):.1f} L' + line(MELT_2026).replace(' ', ' L') + f' L{x(13):.1f},{y(0):.1f} Z'
    s = [f'<svg viewBox="0 0 {w} {h}" width="100%" xmlns="http://www.w3.org/2000/svg" font-family="Noto Sans, sans-serif" font-size="10">',
         '<defs><linearGradient id="melt" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a6fc9" stop-opacity=".45"/>'
         '<stop offset="1" stop-color="#3a6fc9" stop-opacity="0"/></linearGradient></defs>',
         # the 8-10 July warm spell, deliberately unlabelled (a reviewer's note asks for a label)
         f'<rect x="{x(6) + 10:.1f}" y="{t}" width="{x(9) - x(6) - 20:.1f}" height="{h - t - b}" fill="#f0b45a" fill-opacity=".16"/>']
    for v in range(0, 50, 10):
        s.append(f'<line x1="{l}" x2="{w - r}" y1="{y(v):.1f}" y2="{y(v):.1f}" stroke="#dde3ea" stroke-width=".8"/>'
                 f'<text x="{l - 6}" y="{y(v) + 3.5:.1f}" text-anchor="end" fill="#5a6577">{v}</text>')
    for i in range(len(MELT_2026)):
        s.append(f'<text x="{x(i):.1f}" y="{h - 8}" text-anchor="middle" fill="#5a6577">{i + 2}</text>')
    s += [f'<path d="{area}" fill="url(#melt)"/>',
          f'<polyline points="{line(MELT_2019)}" fill="none" stroke="#8a94a6" stroke-width="1.6" stroke-dasharray="5 4"/>',
          f'<polyline points="{line(MELT_2026)}" fill="none" stroke="#272485" stroke-width="2.4" stroke-linejoin="round"/>']
    s += [f'<circle cx="{x(i):.1f}" cy="{y(v):.1f}" r="2.6" fill="#fff" stroke="#272485" stroke-width="1.6"/>' for i, v in enumerate(MELT_2026)]
    s += [f'<g transform="translate({w - 150},{t + 6})"><line x1="0" x2="18" y1="0" y2="0" stroke="#272485" stroke-width="2.4"/>'
          '<text x="24" y="3.5" fill="#1b2330">2026</text><line x1="64" x2="82" y1="0" y2="0" stroke="#8a94a6" stroke-width="1.6" stroke-dasharray="5 4"/>'
          '<text x="88" y="3.5" fill="#1b2330">2019</text></g>', '</svg>']
    return ''.join(s)

# ---------------------------------------------------------------------------------------------- form fields
# One list drives both the boxes drawn in the HTML and the AcroForm fields laid over them (mm, page-relative).

FIELDS = [
    dict(name='lead', kind='text', label='Expedition lead', x=18, y=126, w=82, h=9, value='Mara Lindqvist'),
    dict(name='lead_date', kind='text', label='Date', x=108, y=126, w=84, h=9, value=''),
    dict(name='archived', kind='check', label='Cores, pits and radar files archived', x=18, y=145, w=5, h=5, value=True),
    dict(name='log_redacted', kind='check', label='Field log redacted (Section 2)', x=18, y=153, w=5, h=5, value=False),
    dict(name='release', kind='check', label='Approved for public release', x=18, y=161, w=5, h=5, value=False),
    dict(name='comments', kind='multiline', label='Comments', x=18, y=177, w=174, h=30, value=''),
    dict(name='data_manager', kind='text', label='Data manager', x=18, y=219, w=82, h=9, value=''),
    dict(name='signature', kind='sig', label='Signature, expedition lead', x=18, y=238, w=100, h=24, value=None),
]


def fields_html():
    out = []
    for f in FIELDS:
        if f['kind'] == 'check':
            out.append(f'<div class="field-box" style="left:{f["x"]}mm;top:{f["y"]}mm;width:{f["w"]}mm;height:{f["h"]}mm"></div>'
                       f'<div class="check-text" style="left:{f["x"] + 8}mm;top:{f["y"] - .4}mm">{html.escape(f["label"])}</div>')
        else:
            cls = 'field-box sig' if f['kind'] == 'sig' else 'field-box'
            out.append(f'<div class="field-label" style="left:{f["x"]}mm;top:{f["y"] - 4.5}mm">{html.escape(f["label"])}</div>'
                       f'<div class="{cls}" style="left:{f["x"]}mm;top:{f["y"]}mm;width:{f["w"]}mm;height:{f["h"]}mm"></div>')
    return '\n  '.join(out)


def pdf_rect(f):
    x0, y1 = f['x'] * MM, PAGE_H_PT - f['y'] * MM
    return [x0, y1 - f['h'] * MM, x0 + f['w'] * MM, y1]

# ---------------------------------------------------------------------------------------------- annotations

YELLOW, PINK, GREEN, BLUE = (1, .86, .3), (1, .62, .78), (.55, .87, .55), (.55, .76, 1)
# highlights: anchor id from report.html -> (page index, author, colour, comment)
HIGHLIGHTS = {
    'h1': (1, 'Tomás Okafor', YELLOW, 'Cores were 14 m at K1 only. Give the depth for each station?'),
    'h2': (1, 'Ines Whitcombe', PINK, 'Add the radar uncertainty here: about ±2 m.'),
}
# sticky notes: (page index, x mm, y mm, author, colour, comment)
NOTES = [
    (0, 178, 92, 'Ines Whitcombe', BLUE, 'Could we use the K4 icefall photo on the cover instead?'),
    (1, 194, 118, 'Devraj Halloran', BLUE, 'K6 change is from the outbound visit only. Footnote it in the table.'),
    (1, 194, 190, 'Mara Lindqvist', GREEN, 'Figure 1: label the 8-10 July warm spell on the chart.'),
    (2, 194, 58, 'Mara Lindqvist', YELLOW, 'Redact names, phone numbers and permits before release. Keep dates and entries.'),
]
WHEN = 'D:20260716093000Z'


def stream(writer, content, bbox, resources=None):
    s = DecodedStreamObject()
    s.set_data(content.encode('latin-1'))
    s.update({NameObject('/Type'): NameObject('/XObject'), NameObject('/Subtype'): NameObject('/Form'),
              NameObject('/BBox'): ArrayObject([FloatObject(v) for v in bbox])})
    if resources is not None:
        s[NameObject('/Resources')] = resources
    return writer._add_object(s)


def add_annot(writer, page, annot):
    ref = writer._add_object(annot)
    page.setdefault(NameObject('/Annots'), ArrayObject()).append(ref)
    return ref


def add_popup(writer, page, parent_ref, rect):
    x0, y0, x1, y1 = rect
    # beside the annotation, on whichever side has room on the page
    px0 = x1 + 6 if x1 + 186 <= PAGE_W_PT else x0 - 186
    popup = DictionaryObject({NameObject('/Type'): NameObject('/Annot'), NameObject('/Subtype'): NameObject('/Popup'),
                              NameObject('/Rect'): ArrayObject([FloatObject(v) for v in (px0, y1 - 90, px0 + 180, y1)]),
                              NameObject('/Parent'): parent_ref, NameObject('/Open'): BooleanObject(False)})
    pref = add_annot(writer, page, popup)
    parent_ref.get_object()[NameObject('/Popup')] = pref


def markup_common(author, colour, text):
    return {NameObject('/Type'): NameObject('/Annot'), NameObject('/T'): TextStringObject(author),
            NameObject('/Contents'): TextStringObject(text), NameObject('/C'): ArrayObject([FloatObject(c) for c in colour]),
            NameObject('/M'): TextStringObject(WHEN), NameObject('/CreationDate'): TextStringObject(WHEN),
            NameObject('/F'): NumberObject(4)}


def add_highlight(writer, page, rects_px, page_index, author, colour, text):
    quads, xs, ys = [], [], []
    for left, top, w, h in rects_px:
        top -= page_index * PAGE_H_PX
        x0, x1 = left * PX, (left + w) * PX
        y1 = PAGE_H_PT - top * PX
        y0 = y1 - h * PX
        quads += [x0, y1, x1, y1, x0, y0, x1, y0]
        xs += [x0, x1]; ys += [y0, y1]
    bbox = [min(xs), min(ys), max(xs), max(ys)]
    gs = DictionaryObject({NameObject('/ExtGState'): DictionaryObject({NameObject('/GS0'): DictionaryObject({
        NameObject('/Type'): NameObject('/ExtGState'), NameObject('/BM'): NameObject('/Multiply')})})})
    body = '/GS0 gs %.3f %.3f %.3f rg\n' % colour + ''.join(
        '%.2f %.2f %.2f %.2f re\n' % (quads[i + 4], quads[i + 5], quads[i + 2] - quads[i], quads[i + 1] - quads[i + 5])
        for i in range(0, len(quads), 8)) + 'f\n'
    annot = DictionaryObject(markup_common(author, colour, text))
    annot.update({NameObject('/Subtype'): NameObject('/Highlight'), NameObject('/Rect'): ArrayObject([FloatObject(v) for v in bbox]),
                  NameObject('/QuadPoints'): ArrayObject([FloatObject(v) for v in quads]),
                  NameObject('/AP'): DictionaryObject({NameObject('/N'): stream(writer, body, bbox, gs)})})
    add_popup(writer, page, add_annot(writer, page, annot), bbox)


def add_note(writer, page, x_mm, y_mm, author, colour, text):
    x0, y1 = x_mm * MM, PAGE_H_PT - y_mm * MM
    rect = [x0, y1 - 20, x0 + 20, y1]
    annot = DictionaryObject(markup_common(author, colour, text))
    # a saved appearance (a speech bubble in the note's colour), so a viewer doesn't build one and mark the note
    # as modified today
    icon = ('q 0.6 w 0.25 0.25 0.3 RG %.3f %.3f %.3f rg\n' % colour
            + '2 7 m 2 18 l 18 18 l 18 7 l 9 7 l 5 3 l 6 7 l h B\n'
            + '0.25 0.25 0.3 RG 5 14 m 15 14 l S 5 11 m 13 11 l S Q\n')
    annot.update({NameObject('/Subtype'): NameObject('/Text'), NameObject('/Name'): NameObject('/Comment'),
                  NameObject('/Rect'): ArrayObject([FloatObject(v) for v in rect]), NameObject('/Open'): BooleanObject(False),
                  NameObject('/AP'): DictionaryObject({NameObject('/N'): stream(writer, icon, [0, 0, 20, 20])})})
    add_popup(writer, page, add_annot(writer, page, annot), rect)

# ---------------------------------------------------------------------------------------------- AcroForm


def add_form(writer, page):
    helv = writer._add_object(DictionaryObject({NameObject('/Type'): NameObject('/Font'), NameObject('/Subtype'): NameObject('/Type1'),
                                                NameObject('/BaseFont'): NameObject('/Helvetica'), NameObject('/Encoding'): NameObject('/WinAnsiEncoding')}))
    zadb = writer._add_object(DictionaryObject({NameObject('/Type'): NameObject('/Font'), NameObject('/Subtype'): NameObject('/Type1'),
                                                NameObject('/BaseFont'): NameObject('/ZapfDingbats')}))
    dr = DictionaryObject({NameObject('/Font'): DictionaryObject({NameObject('/Helv'): helv, NameObject('/ZaDb'): zadb})})
    fields = ArrayObject()
    for f in FIELDS:
        rect = pdf_rect(f)
        w, h = rect[2] - rect[0], rect[3] - rect[1]
        d = DictionaryObject({NameObject('/Type'): NameObject('/Annot'), NameObject('/Subtype'): NameObject('/Widget'),
                              NameObject('/T'): TextStringObject(f['name']), NameObject('/TU'): TextStringObject(f['label']),
                              NameObject('/Rect'): ArrayObject([FloatObject(v) for v in rect]), NameObject('/F'): NumberObject(4),
                              NameObject('/P'): page.indirect_reference})
        if f['kind'] in ('text', 'multiline'):
            size = 10
            d.update({NameObject('/FT'): NameObject('/Tx'), NameObject('/DA'): TextStringObject(f'/Helv {size} Tf 0.11 0.14 0.19 rg'),
                      NameObject('/V'): TextStringObject(f['value'])})
            if f['kind'] == 'multiline':
                d[NameObject('/Ff')] = NumberObject(4096)
            text = f['value'].replace('\\', '\\\\').replace('(', '\\(').replace(')', '\\)')
            ty = (h - size * .72) / 2 if f['kind'] == 'text' else h - size - 3
            body = f'/Tx BMC q BT /Helv {size} Tf 0.11 0.14 0.19 rg 3 {ty:.2f} Td ({text}) Tj ET Q EMC\n' if text else '/Tx BMC EMC\n'
            d[NameObject('/AP')] = DictionaryObject({NameObject('/N'): stream(writer, body, [0, 0, w, h], dr)})
        elif f['kind'] == 'check':
            on = stream(writer, f'q BT /ZaDb {h - 2:.2f} Tf 0.15 0.14 0.52 rg 1.1 1.4 Td (4) Tj ET Q\n', [0, 0, w, h], dr)
            off = stream(writer, '', [0, 0, w, h])
            state = NameObject('/Yes' if f['value'] else '/Off')
            d.update({NameObject('/FT'): NameObject('/Btn'), NameObject('/V'): state, NameObject('/AS'): state,
                      NameObject('/DA'): TextStringObject('/ZaDb 0 Tf 0.15 0.14 0.52 rg'),
                      NameObject('/MK'): DictionaryObject({NameObject('/CA'): TextStringObject('4')}),
                      NameObject('/AP'): DictionaryObject({NameObject('/N'): DictionaryObject({NameObject('/Yes'): on, NameObject('/Off'): off})})})
        elif f['kind'] == 'sig':
            d.update({NameObject('/FT'): NameObject('/Sig'),
                      NameObject('/AP'): DictionaryObject({NameObject('/N'): stream(writer, '', [0, 0, w, h])})})
        ref = add_annot(writer, page, d)
        fields.append(ref)
    writer._root_object[NameObject('/AcroForm')] = writer._add_object(DictionaryObject({
        NameObject('/Fields'): fields, NameObject('/DR'): dr, NameObject('/DA'): TextStringObject('/Helv 0 Tf 0 g'),
        NameObject('/SigFlags'): NumberObject(1)}))

# ---------------------------------------------------------------------------------------------- build


def chrome(*args):
    return subprocess.run([CHROME, '--headless=new', '--disable-gpu', '--allow-file-access-from-files', '--virtual-time-budget=5000', *args],
                          capture_output=True, text=True, check=True).stdout


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--photo', default=os.path.join(HERE, 'cover.jpg'),
                    help='cover photo (JPEG or PNG); a drawn placeholder is used when the file is missing')
    ap.add_argument('--out', default=os.path.join(HERE, 'expedition-report.pdf'))
    a = ap.parse_args()

    page = open(os.path.join(HERE, 'report.html'), encoding='utf-8').read()
    page = page.replace('{{COVER_PHOTO}}', cover_photo(a.photo)).replace('{{MELT_CHART}}', melt_chart()).replace('{{FIELDS}}', fields_html())
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, 'report.html')
        open(src, 'w', encoding='utf-8').write(page)
        dom = chrome('--window-size=794,1123', '--dump-dom', f'file://{src}#anchors')
        anchors = {a['id']: a['rects'] for a in json.loads(html.unescape(re.search(r'<pre id="anchors">(.*?)</pre>', dom, re.S).group(1)))}
        raw = os.path.join(tmp, 'raw.pdf')
        chrome('--no-pdf-header-footer', f'--print-to-pdf={raw}', f'file://{src}')
        reader = PdfReader(raw)
        writer = PdfWriter(clone_from=reader)

    for hid, (pi, author, colour, text) in HIGHLIGHTS.items():
        add_highlight(writer, writer.pages[pi], anchors[hid], pi, author, colour, text)
    for pi, x, y, author, colour, text in NOTES:
        add_note(writer, writer.pages[pi], x, y, author, colour, text)
    add_form(writer, writer.pages[3])
    writer.add_metadata({'/Title': 'Kestrel Icefield Survey: Field Report 2026', '/Author': 'North Ridge Glaciology Group (fictional)',
                         '/Subject': 'icepdf showcase document. All people, places and numbers are fictional.',
                         '/Creator': 'icepdf showcase build (HTML + Chrome + pypdf)'})
    with open(a.out, 'wb') as fh:
        writer.write(fh)
    print(a.out, len(writer.pages), 'pages')


if __name__ == '__main__':
    main()
