# JPEG 2000 decisions and observations (GH-613)

The `INFERRED` evidence log from `JPX-GH613-PLAN.md` §1b. Each entry: what we observed, how to
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
  (plan §1a), so it's expected to score EXACT. For JJ2000, MISMATCH is the recorded baseline.
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
- **Decision:** the new reader throws IIOException only (plan §1a). No core change on this branch;
  revisit when switching over.

## D-8 Corpus census: the corpus exercises very little of Part 1 (2026-10-17)

- **Observed:** see `JPX-GH613-PLAN.md` §2.2 results.
- **Decision:** corpus parity is necessary but tells us little about a decoder's completeness.
  The synthetic fixtures are the completeness bar. Option B (port OpenJPEG) stands; jpx.js parity
  would pass the corpus while failing much of the fixture matrix.
