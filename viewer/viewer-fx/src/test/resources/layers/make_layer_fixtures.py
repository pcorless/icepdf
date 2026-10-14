"""Builds nested_layers.pdf: optional content in a nested /Order and a labelled collection,
plus /PageLayout /TwoPageRight - for the layers panel and document layout tests.

    /Order [ A [ B C ] (Labelled) D ]   -> A with children B, C; then a label holding D
"""
import os


def build():
    objs = {}
    objs[1] = (b"<< /Type /Catalog /Pages 2 0 R /PageLayout /TwoPageRight "
               b"/OCProperties << /OCGs [5 0 R 6 0 R 7 0 R 8 0 R] "
               b"/D << /Order [5 0 R [6 0 R 7 0 R] [(Labelled) 8 0 R]] /OFF [] >> >> >>")
    objs[2] = b"<< /Type /Pages /Count 1 /Kids [3 0 R] >>"
    objs[3] = b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R >>"
    content = b"0 0 1 rg 20 20 50 50 re f"
    objs[4] = b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream"
    for n, name in zip((5, 6, 7, 8), (b"A", b"B", b"C", b"D")):
        objs[n] = b"<< /Type /OCG /Name (" + name + b") >>"
    out = b"%PDF-1.5\n"
    offsets = {}
    for n in sorted(objs):
        offsets[n] = len(out)
        out += b"%d 0 obj\n" % n + objs[n] + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    for n in sorted(objs):
        out += b"%010d 00000 n \n" % offsets[n]
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)
    return out


if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(here, "nested_layers.pdf"), "wb") as f:
        f.write(build())
