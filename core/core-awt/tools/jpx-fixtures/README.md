# JPEG 2000 fixtures (GH-613)

Licence-clean JPEG 2000 test streams and reference decodes for `src/test/resources/jpx`, plus the
tests that score any installed ImageIO reader against them. Requirements: `IMAGEIO-CONTRACT.md`;
audit-trail rules: `PROVENANCE-RULES.md`; evidence log: `DECISIONS.md`.

Everything here is generated from images synthesised by `generate.py`, so all of it is ours
(Apache 2.0). No corpus or conformance-suite file is ever committed.

## Layout

| Path | What |
|---|---|
| `generate.py` | builds the fixture matrix, derived fixtures (PPM/PPT, COC/QCC, JP2 palette/ICC/cdef) and hostile streams; checks every lossless case round-trips |
| `jpxref.c` | reference decoder: libopenjp2 API, no colour handling, one PGX per component |
| `IMAGEIO-CONTRACT.md` | what a JPEG 2000 reader must do to be a drop-in ImageIO plug-in |
| `PROVENANCE-RULES.md` | origin tags (PORTED/ADAPTED/SPEC/ORIGINAL/INFERRED) and where they're recorded |
| `DECISIONS.md` | observations that drove a choice (the `INFERRED` evidence log) |
| `src/test/resources/jpx/*.j2k, *.jp2` | fixtures |
| `src/test/resources/jpx/ref/*.pgx` | reference components (`PG ML <+/-> prec w h`, big-endian samples) |
| `src/test/resources/jpx/hostile/` | damaged / malicious streams derived from `rev53-rgb.j2k` |
| `src/test/resources/jpx/manifest.json` | per fixture: encode command, oracle, hashes, notes |
| `src/test/resources/jpx/references.tsv`, `hostile.txt` | what the Java tests iterate |
| `src/test/resources/jpx/baseline/<suite>-<reader>.tsv` | pinned outcome per reader (owned by the tests) |

## Rebuilding the fixtures

Only needed when the matrix changes. The generator is deterministic (same hashes run to run).

```
git clone --branch v2.5.4 https://github.com/uclouvain/openjpeg.git ~/dev/git/openjpeg   # 6c4a29b0
cd ~/dev/git/openjpeg && mkdir build-strict && cd build-strict
cmake .. -DCMAKE_BUILD_TYPE=RelWithDebInfo -DBUILD_SHARED_LIBS=OFF -DBUILD_THIRDPARTY=OFF && make -j8
gcc -O2 -std=c99 -o bin/jpxref <icepdf>/core/core-awt/tools/jpx-fixtures/jpxref.c \
    -I../src/lib/openjp2 -Isrc/lib/openjp2 bin/libopenjp2.a -lm -lpthread
cd <icepdf> && python3 -I core/core-awt/tools/jpx-fixtures/generate.py      # OPJ_BIN overrides the tool dir
```

**Not `Release`:** OpenJPEG's Release build adds `-ffast-math`. The float reordering it allows
changes 9/7 encodes and moves some decoded samples by one, and the result depends on the
compiler version (DECISIONS.md D-13). RelWithDebInfo keeps strict IEEE float.

`baseline/` is left alone. Re-bless afterwards (below) if outcomes moved.

## Tests

```
./gradlew :core:core-awt:test --tests 'org.icepdf.core.pobjects.graphics.images.jpx.*' \
    --tests '*Jpeg2000TilePartsRepairTest'
```

| Test | Checks |
|---|---|
| `Jpeg2000ConformanceTest` | each fixture decoded like ICEpdf does (`read(0, param).getRaster()`) vs the reference: EXACT / EQUIVALENT / CLOSE / MISMATCH / ERROR / TIMEOUT |
| `Jpeg2000ImageReaderContractTest` | the ImageIO plug-in surface (`IMAGEIO-CONTRACT.md`): PASS / FAIL per check |
| `Jpeg2000HostileInputTest` | damaged streams end in bounded time without OOM or a hang |
| `Jpeg2000TilePartsRepairTest` | ICEpdf's TNsot repair restores the GH-553 stream and it decodes exactly |

The first three pin **each reader's** outcomes in `baseline/`, so they pass for JJ2000 with its
gaps on record, and fail when any outcome changes. After a deliberate change:

```
./gradlew :core:core-awt:test --tests '...jpx.*' -Djpx.bless=true     # rewrites baseline/<suite>-<reader>.tsv
git diff core/core-awt/src/test/resources/jpx/baseline                    # review what moved
```

`-Djpx.reader=<SPI class name fragment>` picks the reader when more than one is installed. Full
reports (with max error / PSNR detail) are written to `build/reports/jpx/` on every run.

The new reader (icepdf-jpx, `~/dev/git/jpx-imageio`, not published yet) is added with
`-PjpxJar=<path to icepdf-jpx jar>`; its baselines are `baseline/*-jpeg2000imagereaderspi.tsv`:

```
./gradlew :core:core-awt:test --tests '...jpx.*' \
    -PjpxJar=$HOME/dev/git/jpx-imageio/build/libs/icepdf-jpx-0.1.0-SNAPSHOT.jar \
    -Djpx.reader=Jpeg2000ImageReaderSpi
```

| Suite | JJ2000 1.4.0 | icepdf-jpx 0.1.0 (2026-10-17) |
|---|---|---|
| conformance (68 decodes) | 48 EXACT, 3 EQUIVALENT, 9 CLOSE, 4 MISMATCH, 2 ERROR, 2 TIMEOUT | 65 EXACT, 3 EQUIVALENT (level-shifted signed, upsampled 4:2:0 / 4:2:2) |
| contract (28 checks) | 23 PASS, 5 FAIL | 28 PASS |
| hostile (18 inputs) | 4 decoded, 14 unchecked exceptions | 6 decoded, 12 `JpxException` (an IOException), all < 1 s |

A reader that hangs can't be stopped; the test gives up after 10 s, records TIMEOUT and leaves the
thread spinning until the JVM exits (JJ2000 does this on `ppm` and `ppt`).

## Provenance

All files here: `ORIGINAL`. `jpxref.c` links OpenJPEG (BSD-2) through its public API and copies no
OpenJPEG code; it's a dev tool and never ships. OpenJPEG pinned at v2.5.4 (`6c4a29b0`), RelWithDebInfo build.
