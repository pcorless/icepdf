# JPEG 2000 tests (GH-613)

The JPEG 2000 decoder, its fixtures and their generator live in **jpx-imageio**
(`~/dev/git/jpx-imageio`, `io.github.pcorless:jpx-imageio`; not published yet):

| What | Where in jpx-imageio |
|---|---|
| fixture generator, reference decoder | `tools/fixtures/generate.py`, `tools/fixtures/jpxref.c` (and its README: how to build OpenJPEG, why not Release) |
| fixtures and references | `src/test/resources/fixtures` |
| ImageIO contract a JPEG 2000 reader must meet | `docs/IMAGEIO-CONTRACT.md` |
| provenance rules, decisions log | `docs/PROVENANCE-RULES.md`, `docs/DECISIONS.md` |

`core/core-awt/src/test/resources/jpx` is a copy of jpx-imageio's fixtures (byte-identical at
jpx-imageio 21940e5) plus `baseline/`, which only ICEpdf has. Refresh it by copying the fixture
folder over, keeping `baseline/`.

## Tests here

ICEpdf's own JPEG 2000 path, with whichever reader is installed:

| Test | Checks |
|---|---|
| `JpxDecoderTest` | JPXDecode images through `ImageStream.getImage`: the PDF's /ColorSpace applied to the reader's raster (exact pixels for lossless RGB, palette and grey), DeviceCMYK, the TNsot repair, subsampling of really big images |
| `Jpeg2000TilePartsRepairTest` | ICEpdf's TNsot repair restores the GH-553 stream and it decodes exactly |
| `Jpeg2000ConformanceTest` | each fixture decoded as ICEpdf does vs the reference: EXACT / EQUIVALENT / CLOSE / MISMATCH / ERROR / TIMEOUT |
| `Jpeg2000ImageReaderContractTest` | the ImageIO plug-in surface: PASS / FAIL per check |
| `Jpeg2000HostileInputTest` | damaged streams end in bounded time without OOM or a hang |

The last three pin **each reader's** outcomes in `baseline/<suite>-<reader>.tsv`, so they pass for
JJ2000 with its gaps on record and fail when an outcome changes. After a deliberate change:

```
./gradlew :core:core-awt:test --tests '...jpx.*' -Djpx.bless=true     # rewrites baseline/<suite>-<reader>.tsv
git diff core/core-awt/src/test/resources/jpx/baseline                    # review what moved
```

By default the tests use JJ2000. `-PjpxJar=<jar>` runs them with jpx-imageio **instead** (JpxDecoder
takes the first reader ImageIO offers, so the two aren't installed together):

```
./gradlew :core:core-awt:test --tests '*JpxDecoderTest' --tests '*Jpeg2000*' \
    -PjpxJar=$HOME/dev/git/jpx-imageio/build/libs/jpx-imageio-0.1.0-SNAPSHOT.jar
```

| Suite | JJ2000 1.4.0 | jpx-imageio 0.1.0 (2026-10-17) |
|---|---|---|
| conformance (68 decodes) | 48 EXACT, 3 EQUIVALENT, 9 CLOSE, 4 MISMATCH, 2 ERROR, 2 TIMEOUT | 65 EXACT, 3 EQUIVALENT (level-shifted signed, upsampled 4:2:0 / 4:2:2) |
| contract (28 checks) | 23 PASS, 5 FAIL | 28 PASS |
| hostile (18 inputs) | 4 decoded, 14 unchecked exceptions | 6 decoded, 12 `JpxException` (an IOException), all < 1 s |
| JpxDecoderTest | palette case skipped: JJ2000 returns palette indices, which the DeviceRGB path can't take | all fixture cases pass (the two synthetic ones need a JPEG 2000 writer, which only JJ2000 has) |

A reader that hangs can't be stopped; the test gives up after 10 s, records TIMEOUT and leaves the
thread spinning until the JVM exits (JJ2000 does this on `ppm` and `ppt`).
