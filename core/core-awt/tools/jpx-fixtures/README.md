# JPEG 2000 fixtures (GH-613)

Licence-clean JPEG 2000 test streams and reference decodes for `src/test/resources/jpx`, plus the
tests that score any installed ImageIO reader against them. Plan: repo-root `JPX-GH613-PLAN.md`.

Everything here is generated from images synthesised by `generate.py`, so all of it is ours
(Apache 2.0). No corpus or conformance-suite file is ever committed.

## Layout

| Path | What |
|---|---|
| `generate.py` | builds the fixture matrix, derived fixtures (PPM/PPT, COC/QCC, JP2 palette/ICC/cdef) and hostile streams; checks every lossless case round-trips |
| `jpxref.c` | reference decoder: libopenjp2 API, no colour handling, one PGX per component |
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
cd ~/dev/git/openjpeg && mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DBUILD_THIRDPARTY=OFF && make -j8
gcc -O2 -std=c99 -o bin/jpxref <icepdf>/core/core-awt/tools/jpx-fixtures/jpxref.c \
    -I../src/lib/openjp2 -Isrc/lib/openjp2 bin/libopenjp2.a -lm -lpthread
cd <icepdf> && python3 -I core/core-awt/tools/jpx-fixtures/generate.py      # OPJ_BIN overrides the tool dir
```

`baseline/` is left alone. Re-bless afterwards (below) if outcomes moved.

## Tests

```
./gradlew :core:core-awt:test --tests 'org.icepdf.core.pobjects.graphics.images.jpx.*' \
    --tests '*Jpeg2000TilePartsRepairTest'
```

| Test | Checks |
|---|---|
| `Jpeg2000ConformanceTest` | each fixture decoded like ICEpdf does (`read(0, param).getRaster()`) vs the reference: EXACT / EQUIVALENT / CLOSE / MISMATCH / ERROR / TIMEOUT |
| `Jpeg2000ImageReaderContractTest` | the ImageIO plug-in surface (plan §1a): PASS / FAIL per check |
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

A reader that hangs can't be stopped; the test gives up after 10 s, records TIMEOUT and leaves the
thread spinning until the JVM exits (JJ2000 does this on `ppm` and `ppt`).

## Provenance

All files here: `ORIGINAL`. `jpxref.c` links OpenJPEG (BSD-2) through its public API and copies no
OpenJPEG code; it's a dev tool and never ships. OpenJPEG pinned at v2.5.4 (`6c4a29b0`).
