# JPEG 2000 decisions and observations (GH-613)

The `INFERRED` evidence log required by `PROVENANCE-RULES.md`. Each entry: what we observed, how to
reproduce it, and what it decided. This file moves to the decoder repository when that exists.

Oracle versions: OpenJPEG v2.5.4 (`6c4a29b0`, built locally), jai-imageio-jpeg2000 1.4.0 (JJ2000).

---

## D-1 opj_decompress is not a raw-sample oracle (2026-10-17)

- **Observed:** `opj_decompress` RAW output is little-endian whatever the docs say
  (`imagetoraw_common` ignores its `big_endian` flag). It refuses components with different
  subsampling. For a 3-component J2K with subsampled chroma it guesses sYCC and writes upsampled,
  colour-converted RGB.
- **Repro:** `opj_decompress -i subsampled-420.j2k -o x.raw` reports three 64x64 components; the
  stream's components 1-2 are 32x32.
- **Decision:** references come from `jpxref.c` (libopenjp2 API directly, no application colour
  handling, PGX per component at its own size).

## D-2 OpenJPEG rejects a tile whose packet data is empty after PPT lifting (2026-10-17)

- **Observed:** the first PPT fixture, from a low-rate source, had a tile whose packet bodies were all
  empty once the headers moved into PPT, so its tile-part carried zero bytes after SOD. OpenJPEG
  2.5.4: "Failed to decode tile 2/6".
- **Repro:** `sop-eph-tiles` with `-r 40,20,10` on the default "mixed" source, then `lift_headers(.., "ppt")`.
- **Decision:** the fixture source switched to noise at `-r 8,4,2`, so every tile has data.
  **Open:** whether a zero-length tile-part body is legal (T.800 A.4.2) needs a reading of the spec
  and a second decoder. JJ2000 can't judge it, because it hangs on every PPT stream (D-4). It's a
  candidate hostile/edge fixture.

## D-3 tiles-offset: OpenJPEG encoder writes a bad 4-px edge tile (2026-10-17)

- **Observed:** lossless `-t 32,32 -T 5,7 -d 5,7` on 100x70 doesn't round-trip in columns 96..99,
  the 4-px-wide partial tile at canvas x 101..105. The two independent decoders, OpenJPEG and JJ2000,
  return the same wrong values (e.g. 183 for 247; JJ2000 within 1 of OpenJPEG). The same image
  without offsets (`tiles`) round-trips in both.
- **Conclusion:** the bug is on the encoder side (`opj_compress` 2.5.4), not in either decoder.
- **Decision:** the fixture is kept. The reference is the stream's content, and the generator records
  it in `KNOWN_ROUNDTRIP_FAILURES`; the case is scored as lossy. **Open:** report upstream, and find
  out why the two decoders differ by 1 inside that tile.

## D-4 JJ2000 hangs on PPM and PPT (2026-10-17)

- **Observed:** `ppm.j2k` and `ppt.j2k` loop forever in `PktDecoder.readPktHead`. Both are derived
  from `sop-eph-tiles` by moving the packet headers out. OpenJPEG decodes both to exactly the
  source decode, which the generator asserts.
- **Conclusion:** most likely a JJ2000 bug, but not confirmed by a third decoder.
- **Decision:** recorded as TIMEOUT in JJ2000's baseline. The tests time out at 10 s rather than
  hang the build.

## D-5 Subsampled read: resolution levels, not point sampling (2026-10-17)

- **Observed:** JJ2000 `setSourceSubsampling(2^k)` point-samples the full-resolution decode. The
  `reduce.reduceK` references are OpenJPEG's reduced-resolution decode (the LL band), and JJ2000
  differs from them by up to 189.
- **Decision:** the new reader maps power-of-two subsampling onto discarded resolution levels
  (`IMAGEIO-CONTRACT.md`), so it's expected to score EXACT. For JJ2000, MISMATCH is the recorded baseline.
- **ICEpdf impact:** really-big JPX images will render smoother (low-pass instead of aliasing).
  This needs a page-level A/B before switch-over.

## D-6 JJ2000 returns signed samples as two's-complement bits (2026-10-17)

- **Observed:** for `signed8-gray`, JJ2000's raster holds `value & 0xFF` in an unsigned byte
  raster, neither level-shifted nor signed.
- **Decision:** the harness accepts this as EQUIVALENT and names it.
- **Open:** what ICEpdf should hand `JpxDecoder` for signed components. The PDF spec's JPXDecode
  rules need checking before the new reader picks a representation.

## D-7 JJ2000 throws unchecked exceptions on damaged input (2026-10-17)

- **Observed:** 14/18 hostile streams end in RuntimeException, AIOOBE, NPE, IOOBE, and once
  `java.lang.ThreadDeath` (`truncated-mid-packet`). None hangs or OOMs, and four packet-garbage
  streams "decode".
- **ICEpdf impact:** `JpxDecoder.decode` catches only IOException. Runtime exceptions are caught
  further up by the `catch (Exception)` blocks in the image references. `ThreadDeath` is an
  `Error`, so it isn't.
- **Decision:** the new reader throws IIOException only (`IMAGEIO-CONTRACT.md`). No core change on this branch;
  revisit when switching over.

## D-8 Corpus census: the corpus exercises very little of Part 1 (2026-10-17)

- **Observed:** an extractor run over all 5,264 QA PDFs found 3,744 unique JPX streams (8,864
  references) in about 194 documents, written to `~/dev/jpx-corpus/` (local only). A marker census
  (T.800 Annex A/I walker) gave the table below. Both tools, `JpxExtract.java` and `JpxCensus.java`,
  and the full `census.csv` are local scratch in `<repo>/scratchpad/jpx/`, not committed.

  | Feature | Corpus |
  |---|---|
  | container | JP2 100% (no raw codestreams) |
  | colr | enum sRGB 2,691 · CMYK (12) 574 · gray (17) 410 · ICC 67 |
  | palette (`pclr`) | 161 (RGB and CMYK palettes, up to 256 entries) |
  | components | 3: 2,638 · 1: 573 · 4: 533 |
  | precision / sign / subsampling | **all 8-bit unsigned, none subsampled** |
  | transform | 9/7 3,464 · 5/3 280 |
  | progression | RLCP 3,654 · LRCP 90 |
  | levels | 5: 3,689 · 6: 55 |
  | tiled (>1 tile) | 1,799 |
  | TNsot under-count (6 vs 5) | **1,312 (35%)** |
  | code-block style | **0 everywhere** |
  | COC / POC / PPM / PPT / RGN / SOP / EPH / precincts | **none** |
  | QCC / TLM / COM | 498 / 55 / 571 |
  | encoders (COM) | LuraTech 481, Kakadu 88, JasPer 2, unmarked 3,173 |
- **Decision:** corpus parity is necessary but tells us little about a decoder's completeness.
  The synthetic fixtures are the completeness bar. Option B (port OpenJPEG) stands; jpx.js parity
  would pass the corpus while failing much of the fixture matrix.

## D-13 References and fixtures come from a strict-IEEE OpenJPEG build (2026-10-17)

- **Observed:** the new decoder matched every reference except 2 of 4,096 samples of
  `prec16-rgb-irr` component 1, which were off by one. Instrumenting OpenJPEG showed its float
  for one of them was 32766.502 (rounding to 32767 + 32768 = 65535, the new decoder's answer),
  yet the reference said 65534. The reference came from a CMake **Release** build, which adds
  `-ffast-math` (`CMakeLists.txt`: "only set for release"). The same source built without it gives
  65535. Fast-math lets GCC reorder float operations, so 9/7 encodes and decodes depend on the
  build, and can drift with the compiler version.
- **Measured:** regenerating everything with a RelWithDebInfo build (no fast-math) changed 4
  encoded fixtures (`irr97-rgb`, `irr97-cmyk`, `layers-quality`, `prec16-rgb-irr`) and their
  references; all integer (5/3) output was identical. Against the strict references, the new
  decoder is bit-exact on all 68. JJ2000's icepdf baseline outcomes didn't change.
- **Decision:** fixtures and references are built with OpenJPEG v2.5.4 RelWithDebInfo
  (`~/dev/git/openjpeg/build-strict`). Lossy 9/7 is compared bit-exactly against that build:
  the new decoder uses the same float operations in the same order, and Java float is strict IEEE.
