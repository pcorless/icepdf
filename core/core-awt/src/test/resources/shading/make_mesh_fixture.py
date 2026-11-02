#!/usr/bin/env python3
"""Builds mesh_fixture.pdf for MeshFunctionShadingTest: mesh shadings, one per page, each labelled.

Usage: python3 make_mesh_fixture.py mesh_fixture.pdf

Page 1 is a type 2 axial reference.  Pages 2-4 are type 4, 5 and 6 meshes whose Function runs
red -> green -> blue across one triangle pair / lattice cell / patch: the colour must come from the
interpolated t, so the middle is green, not the purple an interpolation of the end colours gives.
Page 5 is a type 5 RGB lattice, page 6 a type 4 DeviceCMYK mesh.

Pages are 400x300.  Each shading is painted with `sh` inside a clip of 20..380 x 40..280.
Mesh data is uncompressed, 16-bit coordinates over Decode [0 400 0 300 ...], so the files can be
read in a text editor.
"""
import sys

W, H = 400, 300
X0, X1, Y0, Y1 = 20, 380, 40, 280


def bits(values):
    """values: list of (value, nbits); returns packed bytes, MSB first, padded at the end."""
    acc, n, out = 0, 0, bytearray()
    for v, b in values:
        acc = (acc << b) | (v & ((1 << b) - 1))
        n += b
        while n >= 8:
            n -= 8
            out.append((acc >> n) & 0xFF)
    if n:
        out.append((acc << (8 - n)) & 0xFF)
    return bytes(out)


def cx(x):
    return (round(x / W * 65535), 16)


def cy(y):
    return (round(y / H * 65535), 16)


def comp(v, nbits=16):
    return (round(v * ((1 << nbits) - 1)), nbits)


# red -> green -> blue, stitched: what t should look like across a mesh.
RGB_STITCH = ("<< /FunctionType 3 /Domain [0 1] /Bounds [0.5] /Encode [0 1 0 1] /Functions ["
              "<< /FunctionType 2 /Domain [0 1] /C0 [1 0 0] /C1 [0 1 0] /N 1 >> "
              "<< /FunctionType 2 /Domain [0 1] /C0 [0 1 0] /C1 [0 0 1] /N 1 >> ] >>")


def rect_triangles_type4(c_left, c_right):
    """Two triangles covering the clip rect; c_* are lists of component values (or [t])."""
    v = [(X0, Y0, c_left), (X1, Y0, c_right), (X0, Y1, c_left),
         (X1, Y0, c_right), (X1, Y1, c_right), (X0, Y1, c_left)]
    out = []
    for i, (x, y, c) in enumerate(v):
        out += [(0, 8), cx(x), cy(y)] + [comp(k) for k in c]
    return bits(out)


def page_type4_fn():
    data = rect_triangles_type4([0], [1])
    sh = ("<< /ShadingType 4 /ColorSpace /DeviceRGB /BitsPerCoordinate 16 /BitsPerComponent 16 "
          f"/BitsPerFlag 8 /Decode [0 {W} 0 {H} 0 1] /Function {RGB_STITCH} /Length {len(data)} >>")
    return "Type 4 + Function: expect red | green | blue", sh, data


def page_type4_cmyk():
    data = rect_triangles_type4([1, 0, 0, 0], [0, 0, 1, 0])
    sh = ("<< /ShadingType 4 /ColorSpace /DeviceCMYK /BitsPerCoordinate 16 /BitsPerComponent 16 "
          f"/BitsPerFlag 8 /Decode [0 {W} 0 {H} 0 1 0 1 0 1 0 1] /Length {len(data)} >>")
    return "Type 4 DeviceCMYK: cyan -> yellow (midpoint C.5 Y.5)", sh, data


def page_type5_rgb():
    # 4 per row x 3 rows, distinct corner colours, a middle row of grey/white.
    cols = [X0, X0 + 120, X0 + 240, X1]
    rows = [Y0, (Y0 + Y1) / 2, Y1]
    colours = [
        [(1, 0, 0), (1, 1, 0), (0, 1, 0), (0, 1, 1)],
        [(1, 1, 1), (0.5, 0.5, 0.5), (0, 0, 0), (1, 1, 1)],
        [(0, 0, 1), (1, 0, 1), (1, 0.5, 0), (0, 0.5, 0)],
    ]
    out = []
    for r, y in enumerate(rows):
        for c, x in enumerate(cols):
            out += [cx(x), cy(y)] + [comp(k) for k in colours[r][c]]
    data = bits(out)
    sh = ("<< /ShadingType 5 /ColorSpace /DeviceRGB /BitsPerCoordinate 16 /BitsPerComponent 16 "
          f"/VerticesPerRow 4 /Decode [0 {W} 0 {H} 0 1 0 1 0 1] /Length {len(data)} >>")
    return "Type 5 RGB lattice 4x3 (bottom R Y G C; mid W gry K W; top B M O dkG)", sh, data


def page_type5_fn():
    out = []
    for y in (Y0, Y1):
        for x, t in ((X0, 0), (X1, 1)):
            out += [cx(x), cy(y), comp(t)]
    data = bits(out)
    sh = ("<< /ShadingType 5 /ColorSpace /DeviceRGB /BitsPerCoordinate 16 /BitsPerComponent 16 "
          f"/VerticesPerRow 2 /Decode [0 {W} 0 {H} 0 1] /Function {RGB_STITCH} /Length {len(data)} >>")
    return "Type 5 + Function: expect red | green | blue", sh, data


def page_type6_fn():
    # one Coons patch over the rect, straight edges; corner t: left 0, right 1.
    xs = [X0, X0 + (X1 - X0) / 3, X0 + 2 * (X1 - X0) / 3, X1]
    ys = [Y0, Y0 + (Y1 - Y0) / 3, Y0 + 2 * (Y1 - Y0) / 3, Y1]
    pts = [(X0, ys[0]), (X0, ys[1]), (X0, ys[2]), (X0, ys[3]),      # up the left edge
           (xs[1], Y1), (xs[2], Y1), (X1, Y1),                      # along the top
           (X1, ys[2]), (X1, ys[1]), (X1, Y0),                      # down the right
           (xs[2], Y0), (xs[1], Y0)]                                 # back along the bottom
    out = [(0, 8)]
    for x, y in pts:
        out += [cx(x), cy(y)]
    for t in (0, 0, 1, 1):  # corners: bottom-left, top-left, top-right, bottom-right
        out.append(comp(t))
    data = bits(out)
    sh = ("<< /ShadingType 6 /ColorSpace /DeviceRGB /BitsPerCoordinate 16 /BitsPerComponent 16 "
          f"/BitsPerFlag 8 /Decode [0 {W} 0 {H} 0 1] /Function {RGB_STITCH} /Length {len(data)} >>")
    return "Type 6 Coons + Function: expect red | green | blue", sh, data


def page_axial_reference():
    sh = (f"<< /ShadingType 2 /ColorSpace /DeviceRGB /Coords [{X0} 0 {X1} 0] /Function {RGB_STITCH} "
          "/Extend [true true] >>")
    return "Reference: type 2 axial with the same Function", sh, None


PAGES = [page_axial_reference, page_type4_fn, page_type5_fn, page_type6_fn, page_type5_rgb, page_type4_cmyk]


def build(path):
    objs = []  # (id, bytes)

    def add(body):
        objs.append(body)
        return len(objs)

    font = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    pages_id = add(None)  # placeholder
    kids = []
    for make in PAGES:
        label, sh, data = make()
        if data is None:
            sh_id = add(sh.encode())
        else:
            sh_id = add(sh.encode() + b"\nstream\n" + data + b"\nendstream")
        content = (f"q {X0} {Y0} {X1 - X0} {Y1 - Y0} re W n /Sh1 sh Q\n"
                   f"BT /F1 11 Tf 20 16 Td ({label}) Tj ET\n").encode()
        c_id = add(f"<< /Length {len(content)} >>\nstream\n".encode() + content + b"\nendstream")
        p_id = add(f"<< /Type /Page /Parent {pages_id} 0 R /MediaBox [0 0 {W} {H}] /Contents {c_id} 0 R "
                   f"/Resources << /Font << /F1 {font} 0 R >> /Shading << /Sh1 {sh_id} 0 R >> >> >>".encode())
        kids.append(p_id)
    objs[pages_id - 1] = (f"<< /Type /Pages /Count {len(kids)} /Kids [" +
                          " ".join(f"{k} 0 R" for k in kids) + "] >>").encode()
    cat = add(f"<< /Type /Catalog /Pages {pages_id} 0 R >>".encode())

    out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, body in enumerate(objs, 1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode()
    for o in offsets:
        out += f"{o:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objs) + 1} /Root {cat} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    open(path, "wb").write(out)


if __name__ == "__main__":
    build(sys.argv[1])
