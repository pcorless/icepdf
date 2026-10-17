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
#
# Provenance: ORIGINAL. Builds the licence-clean JPEG 2000 fixture set for GH-613.
#
# Every source image is synthesised here (gradients, a fixed-seed noise field, a blocky glyph
# pattern), encoded with OpenJPEG's opj_compress across the coding options a Part 1 decoder must
# handle, and decoded back with opj_decompress as the reference. Fixtures, references and the
# manifest (commands, oracle version, hashes) are committed; this script only has to run again
# when the matrix changes.
#
# Usage: python3 -I generate.py [outDir]
#   OPJ_BIN  directory holding opj_compress and jpxref (default ~/dev/git/openjpeg/build-strict/bin).
#            Build OpenJPEG as RelWithDebInfo, NOT Release: Release adds -ffast-math, whose float
#            reordering changes 9/7 encodes and decodes (and drifts with the compiler); see
#            DECISIONS.md D-13.

import hashlib
import json
import os
import random
import shutil
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
OUT = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.join(
    REPO, "core", "core-awt", "src", "test", "resources", "jpx")
OPJ = os.environ.get("OPJ_BIN", os.path.expanduser("~/dev/git/openjpeg/build-strict/bin"))
COMPRESS = os.path.join(OPJ, "opj_compress")
DECOMPRESS = os.path.join(OPJ, "opj_decompress")
JPXREF = os.path.join(OPJ, "jpxref")


# ---------------------------------------------------------------------------------------------
# source images: list of component planes, each a list of rows of ints
# ---------------------------------------------------------------------------------------------

def plane(w, h, fn):
    return [[fn(x, y) for x in range(w)] for y in range(h)]


def gradient(w, h, prec, kind):
    top = (1 << prec) - 1
    if kind == 0:
        return plane(w, h, lambda x, y: (x * top) // max(1, w - 1))
    if kind == 1:
        return plane(w, h, lambda x, y: (y * top) // max(1, h - 1))
    return plane(w, h, lambda x, y: ((x + y) * top) // max(1, w + h - 2))


def noise(w, h, prec, seed):
    rnd = random.Random(seed)
    top = (1 << prec) - 1
    return plane(w, h, lambda x, y: rnd.randint(0, top))


def glyphs(w, h, prec):
    # blocky high-contrast shapes: exercises the significance/cleanup passes like text does
    top = (1 << prec) - 1

    def f(x, y):
        cx, cy = x % 16, y % 16
        on = (2 <= cx <= 3) or (2 <= cy <= 3 and cx < 12) or (cx == cy and cx > 4)
        return 0 if on else top
    return plane(w, h, f)


def source(name, w, h, comps, prec):
    """A deterministic image: component i gets a different pattern."""
    makers = [lambda: gradient(w, h, prec, 0), lambda: glyphs(w, h, prec),
              lambda: noise(w, h, prec, 613), lambda: gradient(w, h, prec, 2)]
    if name == "noise":
        makers = [lambda i=i: noise(w, h, prec, 613 + i) for i in range(4)]
    return [makers[i % len(makers)]() for i in range(comps)]


def subsample(p, dx, dy):
    return [row[::dx] for row in p[::dy]]


def write_raw(path, planes, prec, signed=False):
    """OpenJPEG raw input: planar, 8-bit as bytes, >8-bit as big-endian 16-bit."""
    with open(path, "wb") as f:
        for p in planes:
            for row in p:
                for v in row:
                    if signed:
                        v -= 1 << (prec - 1)
                    if prec <= 8:
                        f.write(struct.pack(">b" if signed else ">B", v))
                    else:
                        f.write(struct.pack(">h" if signed else ">H", v))


# ---------------------------------------------------------------------------------------------
# the matrix
# ---------------------------------------------------------------------------------------------

def case(name, args, w=64, h=64, comps=3, prec=8, img="mixed", sub=None, signed=False,
         ext="j2k", decode=None, patch=None, notes=""):
    return dict(name=name, args=args, w=w, h=h, comps=comps, prec=prec, img=img, sub=sub,
                signed=signed, ext=ext, decode=decode or [{}], patch=patch, notes=notes)


CASES = [
    # transforms and levels
    case("rev53-gray", [], comps=1),
    case("rev53-rgb", []),
    case("irr97-rgb", ["-I"]),
    case("irr97-rgb-rate", ["-I", "-r", "20"]),
    case("rev53-dwt0", ["-n", "1"], notes="no wavelet decomposition"),
    case("rev53-dwt6", ["-n", "7"], notes="6 decomposition levels (seen in the corpus)"),
    case("rev53-cmyk", [], comps=4, notes="4 components, no MCT: CMYK-shaped"),
    case("irr97-cmyk", ["-I"], comps=4),
    case("mct-off-rgb", ["-mct", "0"]),
    case("irr97-mct-off", ["-I", "-mct", "0"]),
    # progression orders
    *[case("prog-" + p.lower(), ["-p", p, "-r", "40,20,10"]) for p in
      ("LRCP", "RLCP", "RPCL", "PCRL", "CPRL")],
    case("poc", ["-POC", "T1=0,0,1,3,3,CPRL/T1=3,0,1,6,3,LRCP", "-n", "6"],
         notes="progression order change in the main header"),
    # code-block styles (each alone, then the combinations encoders actually ship)
    *[case("cblk-" + n, ["-M", str(v)]) for n, v in
      (("bypass", 1), ("reset", 2), ("termall", 4), ("vcausal", 8), ("predterm", 16),
       ("segsym", 32))],
    case("cblk-all", ["-M", "63"]),
    case("cblk-bypass-termall", ["-M", "5", "-I", "-r", "10"]),
    case("cblk-reset-termall-segsym", ["-M", "38"]),
    case("cblk-small", ["-b", "16,16"]),
    case("cblk-wide", ["-b", "64,16"]),
    # layers, precincts, markers
    case("layers3", ["-r", "40,20,10"]),
    case("layers-quality", ["-q", "30,40,50", "-I"]),
    case("precincts", ["-c", "[32,32],[16,16]"]),
    case("sop-eph", ["-SOP", "-EPH", "-r", "40,20,10"]),
    case("sop-eph-tiles", ["-SOP", "-EPH", "-r", "8,4,2", "-t", "32,32"], w=96, h=64, img="noise",
         notes="source for the PPM/PPT derived fixtures"),
    case("rgba", [], comps=4, notes="source for jp2-cdef-rgba"),
    case("plt-tlm", ["-PLT", "-TLM", "-t", "32,32"]),
    case("roi", ["-ROI", "c=0,U=4"], notes="RGN marker, max-shift ROI"),
    case("comment", ["-C", "GH-613 fixture"]),
    # tiling and tile-parts
    case("tiles", ["-t", "32,32"], w=100, h=70, notes="edge tiles are partial"),
    case("tiles-offset", ["-t", "32,32", "-T", "5,7", "-d", "5,7"], w=100, h=70,
         notes="image and tile grid offsets"),
    case("tileparts-res", ["-TP", "R", "-n", "6", "-t", "32,32"], w=96, h=64),
    case("tnsot-undercount", ["-TP", "R", "-n", "6", "-t", "32,32"], w=96, h=64,
         patch="tnsot-minus-one",
         notes="TNsot rewritten to 5 with 6 tile-parts: the GH-553 encoder bug; "
               "reference is the intact stream"),
    # precision, sign, subsampling
    case("prec12-gray", [], comps=1, prec=12),
    case("prec16-gray", [], comps=1, prec=16),
    case("prec16-rgb-irr", ["-I"], prec=16),
    case("prec4-gray", [], comps=1, prec=4),
    case("signed8-gray", [], comps=1, signed=True),
    case("subsampled-420", [], sub=[(1, 1), (2, 2), (2, 2)],
         notes="YCbCr 4:2:0-shaped; MCT not allowed"),
    case("subsampled-422-irr", ["-I"], sub=[(1, 1), (2, 1), (2, 1)]),
    # odd sizes
    case("size-1x1", ["-n", "1"], w=1, h=1),
    case("size-odd", ["-n", "4"], w=37, h=13),
    case("size-tall", ["-n", "2"], w=3, h=200),
    # JP2 container
    case("jp2-rgb", [], ext="jp2"),
    case("jp2-gray", [], comps=1, ext="jp2"),
    case("jp2-cmyk", [], comps=4, ext="jp2"),
    # decoder-side options: reduced resolution and region references
    case("reduce", ["-n", "4"], w=96, h=96,
         decode=[{}, {"reduce": 1}, {"reduce": 2}, {"reduce": 3}]),
    case("region", ["-t", "32,32"], w=96, h=96,
         decode=[{}, {"region": [10, 20, 70, 50]}]),
    case("noise-rev", [], img="noise"),
    case("noise-irr", ["-I", "-r", "8"], img="noise"),
]


# ---------------------------------------------------------------------------------------------
# hostile inputs, derived from a valid stream
# ---------------------------------------------------------------------------------------------

def markers(b):
    """(offset, marker) for main-header markers and each SOT, walking by length and Psot."""
    out, p = [], 2
    while p + 4 <= len(b):
        m = struct.unpack(">H", b[p:p + 2])[0]
        if m == 0xFFD9:
            out.append((p, m))
            break
        out.append((p, m))
        if m == 0xFF90:
            psot = struct.unpack(">I", b[p + 6:p + 10])[0]
            p = p + psot if psot else len(b)
            continue
        p += 2 + struct.unpack(">H", b[p + 2:p + 4])[0]
    return out


def tnsot_minus_one(b):
    b = bytearray(b)
    for off, m in markers(bytes(b)):
        if m == 0xFF90 and b[off + 11] > 1:
            b[off + 11] -= 1
    return bytes(b)


def hostile(base):
    ms = markers(base)
    siz = next(o for o, m in ms if m == 0xFF51)
    sot = next(o for o, m in ms if m == 0xFF90)
    out = {}
    for o, m in ms:
        out["truncated-at-%04x-%d" % (m, o)] = base[:o + 3]
    out["truncated-mid-packet"] = base[:sot + 14 + 7]
    b = bytearray(base)
    b[siz + 2:siz + 4] = struct.pack(">H", 0xFFFF)
    out["siz-length-lies"] = bytes(b)
    b = bytearray(base)
    b[siz + 6:siz + 14] = struct.pack(">II", 0xFFFFFFF0, 0xFFFFFFF0)
    out["siz-huge"] = bytes(b)
    b = bytearray(base)
    b[siz + 22:siz + 30] = struct.pack(">II", 0, 0)
    out["siz-zero-tile"] = bytes(b)
    b = bytearray(base)
    b[siz + 38:siz + 40] = struct.pack(">H", 0)
    out["siz-zero-components"] = bytes(b)
    b = bytearray(base)
    b[sot + 6:sot + 10] = struct.pack(">I", 0x7FFFFFFF)
    out["psot-past-eof"] = bytes(b)
    b = bytearray(base)
    b[sot + 4:sot + 6] = struct.pack(">H", 0xFFFE)
    out["isot-out-of-range"] = bytes(b)
    cod = next(o for o, m in ms if m == 0xFF52)
    b = bytearray(base)
    b[cod + 9] = 33
    out["cod-levels-33"] = bytes(b)
    b = bytearray(base)
    b[cod + 10] = 9
    out["cod-cblk-too-big"] = bytes(b)
    rnd = random.Random(613)
    body = sot + 14
    for i in range(4):
        b = bytearray(base)
        for _ in range(8):
            j = rnd.randrange(body, len(b) - 2)
            b[j] = rnd.randrange(256)
        out["packet-garbage-%d" % i] = bytes(b)
    return out


# ---------------------------------------------------------------------------------------------
# derived fixtures for features opj_compress can't emit: PPM/PPT, COC/QCC, JP2 palette/ICC/cdef
# ---------------------------------------------------------------------------------------------

def seg(marker, body):
    return struct.pack(">HH", marker, len(body) + 2) + body


def split_codestream(cs):
    """(main header bytes after SOC, [ (tile-part header markers [(m, body)], body bytes) ])."""
    ms = markers(cs)
    first_sot = next(o for o, m in ms if m == 0xFF90)
    main = cs[2:first_sot]
    parts = []
    for o, m in ms:
        if m != 0xFF90:
            continue
        psot = struct.unpack(">I", cs[o + 6:o + 10])[0]
        end = o + psot if psot else len(cs) - 2
        sot = cs[o + 4:o + 12]
        q, hdr = o + 12, []
        while struct.unpack(">H", cs[q:q + 2])[0] != 0xFF93:
            mm, ll = struct.unpack(">HH", cs[q:q + 4])
            hdr.append((mm, cs[q + 4:q + 2 + ll]))
            q += 2 + ll
        parts.append((sot, hdr, cs[q + 2:end]))
    return main, parts


def main_markers(main):
    out, p = [], 0
    while p < len(main):
        m, l = struct.unpack(">HH", main[p:p + 4])
        out.append((m, main[p + 4:p + 2 + l]))
        p += 2 + l
    return out


def join_codestream(main_segs, parts):
    out = bytearray(b"\xff\x4f")
    for m, body in main_segs:
        out += seg(m, body)
    for sot, hdr, body in parts:
        hb = b"".join(seg(m, b) for m, b in hdr)
        psot = 12 + len(hb) + 2 + len(body)
        out += struct.pack(">HH", 0xFF90, 10) + sot[:2] + struct.pack(">I", psot) + sot[6:]
        out += hb + b"\xff\x93" + body
    out += b"\xff\xd9"
    return bytes(out)


def split_packets(body):
    """Body of an SOP+EPH tile-part -> [(header, data)]. Marker codes > FF8F can't occur inside
    packet headers or code-block data (bit stuffing), so SOP/EPH are unambiguous."""
    pk, p = [], 0
    while p < len(body):
        assert body[p:p + 2] == b"\xff\x91", "expected SOP at %d" % p
        h0 = p + 6
        e = body.index(b"\xff\x92", h0)
        n = body.find(b"\xff\x91", e + 2)
        n = len(body) if n < 0 else n
        pk.append((body[h0:e], body[e + 2:n]))
        p = n
    return pk


def clear_sop_eph(segs):
    out = []
    for m, b in segs:
        if m == 0xFF52:
            b = bytes([b[0] & ~0x06]) + b[1:]
        out.append((m, b))
    return out


def lift_headers(cs, where):
    """Moves every packet header into PPT (per tile-part) or PPM (main header) marker segments,
    dropping SOP/EPH. Decodes to the same samples as the input."""
    main, parts = split_codestream(cs)
    msegs = clear_sop_eph(main_markers(main))
    new_parts, ppm_data = [], bytearray()
    for sot, hdr, body in parts:
        pk = split_packets(body)
        headers = b"".join(h for h, _ in pk)
        data = b"".join(d for _, d in pk)
        hdr = clear_sop_eph(hdr)
        if where == "ppt":
            chunks = [headers[i:i + 60000] for i in range(0, len(headers), 60000)] or [b""]
            hdr = hdr + [(0xFF61, bytes([z]) + c) for z, c in enumerate(chunks)]
        else:
            ppm_data += struct.pack(">I", len(headers)) + headers
        new_parts.append((sot, hdr, data))
    if where == "ppm":
        chunks = [ppm_data[i:i + 60000] for i in range(0, len(ppm_data), 60000)]
        msegs = msegs + [(0xFF60, bytes([z]) + bytes(c)) for z, c in enumerate(chunks)]
    return join_codestream(msegs, new_parts)


def redundant_coc_qcc(cs):
    """Adds COC and QCC for component 1 that restate COD/QCD: exercises marker precedence and
    parsing without changing the decoded samples."""
    main, parts = split_codestream(cs)
    segs = main_markers(main)
    cod = next(b for m, b in segs if m == 0xFF52)
    qcd = next(b for m, b in segs if m == 0xFF5C)
    out = []
    for m, b in segs:
        out.append((m, b))
        if m == 0xFF5C:
            out.append((0xFF53, bytes([1, cod[0] & 1]) + cod[5:]))
            out.append((0xFF5D, bytes([1]) + qcd))
    return join_codestream(out, parts)


def box(t, body):
    return struct.pack(">I", 8 + len(body)) + t + body


def jp2(cs, w, h, nc, prec, colr, extra=b""):
    ihdr = struct.pack(">IIHBBBB", h, w, nc, prec - 1, 7, 0, 0)
    return (box(b"jP  ", b"\r\n\x87\n") + box(b"ftyp", b"jp2 " + struct.pack(">I", 0) + b"jp2 ")
            + box(b"jp2h", box(b"ihdr", ihdr) + colr + extra) + box(b"jp2c", cs))


def colr_enum(cs_enum):
    return box(b"colr", struct.pack(">BBBI", 1, 0, 0, cs_enum))


def icc_gray_profile():
    """Minimal ICC v2 monitor-class gray profile (header, desc, wtpt, kTRC gamma 2.2, cprt)."""
    def s15(v):
        return struct.pack(">i", int(round(v * 65536)))

    def text_desc(s):
        a = s.encode("ascii") + b"\0"
        return b"desc\0\0\0\0" + struct.pack(">I", len(a)) + a + b"\0" * 12 + b"\0" * 3 + b"\0" * 67
    tags = [(b"desc", text_desc("GH-613 gray")),
            (b"wtpt", b"XYZ \0\0\0\0" + s15(0.9642) + s15(1.0) + s15(0.8249)),
            (b"kTRC", b"curv\0\0\0\0" + struct.pack(">IH", 1, int(2.2 * 256)) + b"\0\0"),
            (b"cprt", b"text\0\0\0\0" + b"Public domain test profile\0")]
    off = 128 + 4 + 12 * len(tags)
    table, data = struct.pack(">I", len(tags)), b""
    for sig, d in tags:
        d += b"\0" * (-len(d) % 4)
        table += sig + struct.pack(">II", off + len(data), len(d))
        data += d
    size = 128 + len(table) + len(data)
    hdr = (struct.pack(">I", size) + b"\0\0\0\0" + struct.pack(">I", 0x02100000) + b"mntrGRAYXYZ "
           + b"\0" * 12 + b"acsp" + b"\0" * 24 + struct.pack(">I", 0) + s15(0.9642) + s15(1.0)
           + s15(0.8249) + b"\0" * 4)
    hdr += b"\0" * (128 - len(hdr))
    return hdr + table + data


def derived(tmp, refs_of):
    """[(name, file, bytes, derivedFrom, reference-must-equal, notes)]."""
    def read(n):
        with open(os.path.join(OUT, n), "rb") as f:
            return f.read()
    out = []
    sop = read("sop-eph-tiles.j2k")
    out.append(("ppt", "ppt.j2k", lift_headers(sop, "ppt"), "sop-eph-tiles.j2k", "sop-eph-tiles",
                "packet headers moved into PPT markers"))
    out.append(("ppm", "ppm.j2k", lift_headers(sop, "ppm"), "sop-eph-tiles.j2k", "sop-eph-tiles",
                "packet headers moved into a PPM marker"))
    out.append(("coc-qcc", "coc-qcc.j2k", redundant_coc_qcc(read("rev53-rgb.j2k")), "rev53-rgb.j2k",
                "rev53-rgb", "COC/QCC restating COD/QCD for component 1 (parse-only)"))
    # palette: 1-component index image + pclr/cmap
    for name, npc, cs_enum, ncol in (("jp2-pclr-rgb", 3, 16, 16), ("jp2-pclr-cmyk", 4, 12, 24)):
        idx = plane(64, 64, lambda x, y: (x // 8 + 3 * (y // 8)) % ncol)
        raw = os.path.join(tmp, name + ".raw")
        write_raw(raw, [idx], 8)
        enc = os.path.join(tmp, name + ".j2k")
        run([COMPRESS, "-i", raw, "-F", "64,64,1,8,u@1x1", "-o", enc])
        with open(enc, "rb") as f:
            cs = f.read()
        rnd = random.Random(ncol)
        entries = bytes(rnd.randrange(256) for _ in range(ncol * npc))
        pclr = box(b"pclr", struct.pack(">HB", ncol, npc) + bytes([7] * npc) + entries)
        cmap = box(b"cmap", b"".join(struct.pack(">HBB", 0, 1, i) for i in range(npc)))
        out.append((name, name + ".jp2", jp2(cs, 64, 64, 1, 8, colr_enum(cs_enum), pclr + cmap),
                    "opj_compress index image", None,
                    "%d-entry palette, %d channels, colr enum %d" % (ncol, npc, cs_enum)))
    gray = read("rev53-gray.j2k")
    icc = icc_gray_profile()
    out.append(("jp2-icc-gray", "jp2-icc-gray.jp2",
                jp2(gray, 64, 64, 1, 8, box(b"colr", struct.pack(">BBB", 2, 0, 0) + icc)),
                "rev53-gray.j2k", "rev53-gray", "restricted ICC gray profile in colr"))
    rgba = read("rgba.j2k")
    cdef = box(b"cdef", struct.pack(">H", 4) + b"".join(
        struct.pack(">HHH", i, 0, i + 1) for i in range(3)) + struct.pack(">HHH", 3, 1, 0))
    out.append(("jp2-cdef-rgba", "jp2-cdef-rgba.jp2", jp2(rgba, 64, 64, 4, 8, colr_enum(16), cdef),
                "rgba.j2k", "rgba", "cdef: channel 3 is opacity (PDF SMaskInData shape)"))
    return out


# ---------------------------------------------------------------------------------------------

def sha1(b):
    return hashlib.sha1(b).hexdigest()


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError("%s\n%s%s" % (" ".join(cmd), r.stdout, r.stderr))
    return r


def oracle_version():
    r = subprocess.run([COMPRESS, "-h"], capture_output=True, text=True)
    for line in (r.stdout + r.stderr).splitlines():
        if "OpenJPEG" in line or "version" in line.lower():
            return line.strip()
    return "unknown"


# Lossless cases where OpenJPEG's own decode does not return the source. Kept as fixtures (they are
# exactly the edge cases a decoder must get right) with the mismatch recorded, not hidden.
KNOWN_ROUNDTRIP_FAILURES = {
    "tiles-offset": "ENCODER bug, OpenJPEG 2.5.4 opj_compress: the 4-px-wide partial edge tile "
                    "(image x 101..105, tile origin 5) is written wrong; OpenJPEG and JJ2000 decode "
                    "the same wrong values in columns 96..99 (JJ2000 within 1), so the reference is "
                    "what the stream says, not the source; see DECISIONS.md D-3",
}


def lossless(c):
    return not any(a in c["args"] for a in ("-I", "-r", "-q")) and c["prec"] <= 16


def read_pgx(path):
    with open(path, "rb") as f:
        b = f.read()
    nl = b.index(b"\n")
    _, _, sign, prec, w, h = b[:nl].decode("ascii").split()
    prec, w, h = int(prec), int(w), int(h)
    n = 1 if prec <= 8 else 2 if prec <= 16 else 4
    data, out = b[nl + 1:], []
    for i in range(w * h):
        v = int.from_bytes(data[i * n:(i + 1) * n], "big", signed=(sign == "-"))
        out.append(v)
    return w, h, out


def reference(path, prefix, d):
    """Decodes with jpxref; returns [(file, sha1)] of the per-component PGX files."""
    args = [JPXREF, path, prefix]
    if d.get("reduce") or d.get("region"):
        args.append(str(d.get("reduce", 0)))
    if d.get("region"):
        args += [str(v) for v in d["region"]]
    n = int(run(args).stdout.strip())
    out = []
    for c in range(n):
        f = "%s.%d.pgx" % (prefix, c)
        with open(f, "rb") as fh:
            out.append(dict(file="ref/" + os.path.basename(f), sha1=sha1(fh.read())))
    return out


def tag_of(d):
    if not d:
        return "full"
    if "region" in d:
        return "region" + "_".join(str(v) for v in d["region"])
    return "reduce%d" % d["reduce"]


def main():
    for tool in (COMPRESS, JPXREF):
        if not os.access(tool, os.X_OK):
            sys.exit("missing %s (set OPJ_BIN; see README.md for building jpxref)" % tool)
    # regenerate everything except baseline/, which the Java tests own (-Djpx.bless=true)
    if os.path.isdir(OUT):
        for entry in os.listdir(OUT):
            if entry == "baseline":
                continue
            path = os.path.join(OUT, entry)
            shutil.rmtree(path) if os.path.isdir(path) else os.remove(path)
    os.makedirs(os.path.join(OUT, "ref"))
    os.makedirs(os.path.join(OUT, "hostile"))
    manifest = dict(
        generator="core/core-awt/tools/jpx-fixtures/generate.py",
        oracle=oracle_version() + " (libopenjp2 via jpxref.c, no application colour handling; "
               "RelWithDebInfo build, IEEE float: no -ffast-math)",
        note="Fixtures and references are generated, licence-clean, ours. References are one PGX "
             "file per decoded component ('PG ML <+|-> prec w h' then big-endian samples), at the "
             "component's own size: subsampled and reduced components are not upsampled.",
        cases=[], hostile=[])
    index = []
    tmp = tempfile.mkdtemp(prefix="jpx-fixtures-")
    try:
        for c in CASES:
            planes = source(c["img"], c["w"], c["h"], c["comps"], c["prec"])
            sub = c["sub"] or [(1, 1)] * c["comps"]
            planes = [subsample(p, dx, dy) for p, (dx, dy) in zip(planes, sub)]
            raw = os.path.join(tmp, c["name"] + ".raw")
            write_raw(raw, planes, c["prec"], c["signed"])
            fmt = "%d,%d,%d,%d,%s@%s" % (c["w"], c["h"], c["comps"], c["prec"],
                                         "s" if c["signed"] else "u",
                                         ":".join("%dx%d" % s for s in sub))
            enc = os.path.join(tmp, c["name"] + "." + c["ext"])
            run([COMPRESS, "-i", raw, "-F", fmt, "-o", enc] + c["args"])
            with open(enc, "rb") as f:
                data = f.read()
            refs = []
            for d in c["decode"]:
                prefix = os.path.join(OUT, "ref", "%s.%s" % (c["name"], tag_of(d)))
                comps = reference(enc, prefix, d)
                refs.append(dict(decode=d, components=comps))
                index.append((c["name"], c["name"] + "." + c["ext"], tag_of(d), len(comps),
                              d.get("reduce", 0), ",".join(str(v) for v in d.get("region", [])),
                              "lossless" if lossless(c) and not c.get("roundTrip") and
                              c["name"] not in KNOWN_ROUNDTRIP_FAILURES else "lossy"))
            if lossless(c):
                # self-check: the fixture really is what the matrix says it is
                off = 1 << (c["prec"] - 1) if c["signed"] else 0
                for i, p in enumerate(planes):
                    w, h, got = read_pgx(os.path.join(OUT, refs[0]["components"][i]["file"]))
                    want = [v - off for row in p for v in row]
                    if (w, h) != (len(p[0]), len(p)) or got != want:
                        if c["name"] in KNOWN_ROUNDTRIP_FAILURES:
                            bad = sum(1 for x, y in zip(got, want) if x != y)
                            c.setdefault("roundTrip", []).append("component %d: %d samples differ" % (i, bad))
                            continue
                        raise RuntimeError("%s component %d does not round-trip" % (c["name"], i))
            if c["patch"] == "tnsot-minus-one":
                data = tnsot_minus_one(data)
            out = os.path.join(OUT, c["name"] + "." + c["ext"])
            with open(out, "wb") as f:
                f.write(data)
            manifest["cases"].append(dict(
                name=c["name"], file=os.path.basename(out), sha1=sha1(data), bytes=len(data),
                width=c["w"], height=c["h"], components=c["comps"], precision=c["prec"],
                signed=c["signed"], subsampling=sub, image=c["img"], lossless=lossless(c),
                encode=["opj_compress", "-i", "<source>.raw", "-F", fmt, "-o", os.path.basename(out)]
                       + c["args"],
                patch=c["patch"], references=refs, notes=c["notes"],
                roundTripFailure=(KNOWN_ROUNDTRIP_FAILURES.get(c["name"], "") + " "
                                  + "; ".join(c.get("roundTrip", []))).strip() or None))
            print("ok", c["name"], len(data), ("lossless, " + ("ROUND-TRIP FAILS (known)" if c.get("roundTrip")
                                                         else "round-trips")) if lossless(c) else "")
        for name, fname, data, src, same_as, notes in derived(tmp, None):
            path = os.path.join(OUT, fname)
            with open(path, "wb") as f:
                f.write(data)
            comps = reference(path, os.path.join(OUT, "ref", name + ".full"), {})
            if same_as:
                want = next(m for m in manifest["cases"] if m["name"] == same_as)
                if [x["sha1"] for x in comps] != \
                        [x["sha1"] for x in want["references"][0]["components"]]:
                    raise RuntimeError("%s does not decode like %s" % (name, same_as))
            manifest["cases"].append(dict(
                name=name, file=fname, sha1=sha1(data), bytes=len(data), derivedFrom=src,
                references=[dict(decode={}, components=comps)], decodesLike=same_as, notes=notes))
            index.append((name, fname, "full", len(comps), 0, "",
                          "lossless" if same_as and next(
                              m for m in manifest["cases"] if m["name"] == same_as).get("lossless")
                          or name.startswith("jp2-pclr") else "lossy"))
            print("ok", name, len(data), "(derived%s)" % (", decodes like " + same_as if same_as else ""))
        with open(os.path.join(OUT, "rev53-rgb.j2k"), "rb") as f:
            base = f.read()
        for name, data in hostile(base).items():
            path = os.path.join(OUT, "hostile", name + ".j2k")
            with open(path, "wb") as f:
                f.write(data)
            manifest["hostile"].append(dict(name=name, file="hostile/" + name + ".j2k",
                                            derivedFrom="rev53-rgb.j2k", sha1=sha1(data),
                                            expect="exception or warning; bounded time and memory"))
        with open(os.path.join(OUT, "manifest.json"), "w") as f:
            json.dump(manifest, f, indent=1)
            f.write("\n")
        with open(os.path.join(OUT, "references.tsv"), "w") as f:
            f.write("# case\tfile\tref\tcomponents\treduce\tregion\tkind\n")
            for row in index:
                f.write("\t".join(str(v) for v in row) + "\n")
        with open(os.path.join(OUT, "hostile.txt"), "w") as f:
            for h in manifest["hostile"]:
                f.write(h["file"] + "\n")
        print("%d cases, %d references, %d hostile -> %s" % (
            len(manifest["cases"]), len(index), len(manifest["hostile"]), OUT))
    finally:
        shutil.rmtree(tmp)


if __name__ == "__main__":
    main()
