# JPEG 2000 tests (GH-613)

ICEpdf decodes JPXDecode images with **jpx-imageio** (`com.github.pcorless:jpx-imageio`, a core
dependency; source `~/dev/git/jpx-imageio`, github.com/pcorless/jpx-imageio). Its fixtures and their
generator live there:

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

ICEpdf's own JPEG 2000 path, with jpx-imageio (JpxDecoder prefers it over any other JPEG 2000 reader
on the class path):

| Test | Checks |
|---|---|
| `JpxDecoderTest` | JPXDecode images through `ImageStream.getImage`: the PDF's /ColorSpace applied to the reader's raster (exact pixels for lossless RGB, palette and grey), /Indexed taking the PDF's table over the JP2 palette, DeviceCMYK, the TNsot under-count (GH-553), the bare-codestream retry, subsampling of really big images |
| `Jp2CodestreamTest` | the codestream comes out of a JP2 file's jp2c box |
| `Jpeg2000ConformanceTest` | each fixture decoded as ICEpdf does vs the reference: EXACT / EQUIVALENT / CLOSE / MISMATCH / ERROR / TIMEOUT |
| `Jpeg2000ImageReaderContractTest` | the ImageIO plug-in surface: PASS / FAIL per check |
| `Jpeg2000HostileInputTest` | damaged streams end in bounded time without OOM or a hang |

The last three pin the reader's outcomes in `baseline/<suite>-<reader>.tsv` and fail when an outcome
changes. After a deliberate change (a new jpx-imageio version, say):

```
./gradlew :core:core-awt:test --tests '...jpx.*' -Djpx.bless=true     # rewrites baseline/<suite>-<reader>.tsv
git diff core/core-awt/src/test/resources/jpx/baseline                    # review what moved
```

To compare another reader, put its jar on the test class path and pick it with
`-Djpx.reader=<SPI class name fragment>`; its baselines are written next to jpx-imageio's.

| Suite | JJ2000 1.4.0 (until 7.5.0) | jpx-imageio 0.1.0 |
|---|---|---|
| conformance (68 decodes) | 48 EXACT, 3 EQUIVALENT, 9 CLOSE, 4 MISMATCH, 2 ERROR, 2 TIMEOUT | 65 EXACT, 3 EQUIVALENT (level-shifted signed, upsampled 4:2:0 / 4:2:2) |
| contract (28 checks) | 23 PASS, 5 FAIL | 28 PASS |
| hostile (18 inputs) | 4 decoded, 14 unchecked exceptions | 6 decoded, 12 `JpxException` (an IOException), all < 1 s |

JJ2000's baselines were removed with it (see git history); it also hung on `ppm` and `ppt`, which the
tests record as TIMEOUT after 10 s, leaving the thread spinning until the JVM exits.
