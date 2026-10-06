# viewer-fx provenance

ICEpdf ships under the Apache License 2.0. This file records everything in `viewer/viewer-fx`
that did not come from a blank editor, so a reviewer can confirm nothing incompatible or
unattributed got in. Keep it current: update it in the same commit that adds a dependency or
adapted code.

## Rules

- New code is written fresh, or ported from ICEpdf's own Apache-2.0 sources (listed below).
- Every new `.java` file carries the repo's Apache 2.0 header (see `add_license_header.sh`).
- A file derived from third-party code keeps its original copyright line and gets an entry
  in the root `NOTICE`, as with the PDFBox `GlyphCache` entry.
- **Never copy** from:
  - OpenJFX sources. GPLv2 + Classpath Exception allows *linking*, not copying, so for
    example `VirtualFlow` and `ScrollPaneSkin` code must not be lifted.
  - Any GPL/LGPL project.
  - StackOverflow (CC BY-SA is not Apache-compatible).
  - Blog or article code with no explicit license.

  Use them for ideas only.
- Before merging, compare
  `./gradlew :viewer:viewer-fx:dependencies --configuration runtimeClasspath` against the
  table below, and check headers with
  `grep -rL "Licensed under the Apache License" viewer/viewer-fx/src --include=*.java`.

## Dependencies

### Shipped (runtime classpath)

| Artifact | Version | License | Notes |
|---|---|---|---|
| `org.openjfx:javafx-base`, `javafx-graphics`, `javafx-controls` | 21.0.5 | GPLv2 + Classpath Exception | Linked only, not bundled in source. Apps supply their own JavaFX runtime. |
| `:core:core-awt` (icepdf-core) | project | Apache 2.0 | Brings its own transitive deps, listed below. |
| `org.bouncycastle:bcprov-jdk18on`, `bcpkix-jdk18on` (+ `bcutil`) | 1.86 | Bouncy Castle Licence (MIT-style) | Also declared directly for signature validation. |

Transitive through core-awt. Already shipped by the Swing viewer, so not new to the project:

| Artifact | Version | License |
|---|---|---|
| `org.apache.pdfbox:fontbox`, `pdfbox-io`, `jbig2-imageio` | 3.0.8 / 3.0.8 / 3.0.4 | Apache 2.0 |
| `commons-logging:commons-logging` | 1.4.0 | Apache 2.0 |
| `com.twelvemonkeys.imageio:imageio-tiff` (+ `imageio-core`, `imageio-metadata`, `common-*`) | 3.15.2 | BSD-3-Clause |
| `com.github.jai-imageio:jai-imageio-core`, `jai-imageio-jpeg2000` | 1.4.0 | BSD-3-Clause (Sun) / JJ2000 licence: verify the JJ2000 terms before a binary release |

### Not shipped (test scope)

| Artifact | Version | License | Why |
|---|---|---|---|
| `org.jfree:org.jfree.fxgraphics2d` | 2.1 | BSD-3-Clause | RasterBench path C (Graphics2D → `Canvas`). If it ever moves to runtime, add a NOTICE entry. |
| `org.openjfx:javafx-swing` | 21.0.5 | GPLv2 + Classpath Exception | RasterBench path A (`SwingFXUtils`). The control itself must not need it. |
| `org.junit.jupiter:junit-jupiter` | BOM 5.14.4 | EPL-2.0 | Tests. |

## Adapted code

Third-party code adapted into this module. **Goal: this table stays empty.**

| Our file | Source (URL + version/commit) | Source license | What was taken |
|---|---|---|---|
| none | | | |

## Ported from ICEpdf (Apache 2.0, same project)

Logic carried over from the Swing viewer or core, recorded so the lineage is visible.

| Our file | ICEpdf source | What was taken |
|---|---|---|
| `view/DocumentLayout`, `view/ViewMode` | viewer-awt `OneColumnPageViewLayout`, `TwoColumnPageViewLayout`, `OnePageViewLayout`, `TwoPageViewLayout`, `DocumentViewControllerImpl` view types | Behaviour only: the six view types (one page / column, two page / column, left or right cover) and vertical centring when content is shorter than the viewport. The code is new: no Swing types, and a pure function of page sizes. |
| `view/TileRenderer` | viewer-awt `AbstractPageViewComponent.PageImageCaptureTask` | The Graphics2D set-up for a clipped region paint: clip, translate to region origin, scale to device px, `page.paint(SCREEN, …)`. Rewritten around tiles and regions. |
| `view/PageTransforms` | core `Page.getPageTransform` | Called, not copied: every FX mapping is derived from the core's own transform. |
| core `DocumentSelection` (added for this module) | viewer-awt `DocumentTextSelection`, `TextSelectionSupport.rangeForPage` / `selectedText` | The anchor/focus semantics, the per-page range rule and the multi-page text join, reimplemented as an immutable core value. Its tests port `DocumentTextSelectionTest`'s cases. |
| `view/PanHandler` | viewer-fx phase 1 skin | The drag-to-scroll code moved into a tool handler. |
| `view/SelectionController` | viewer-awt `TextSelection` (`selectionStart`, `selection`, `columnAwareCaretOffset`, `selectRangeAtPoint`) | The gesture semantics and the D4 column rule (constrain only the nearest-line fallback to the pointer's column, else the anchor column in a gutter; never constrain a direct glyph hit). Rewritten as a toolkit-free class over `DocumentSelection`. |

Test fixtures: `SelectionControllerTest` reads `test_print.pdf` and
`windrivercasestudy1n3d2m8km0r.pdf` from `viewer/viewer-awt/src/test/resources/redact/` in place.
They are not copied into this module.

## Reference material (ideas only, no code copied)

- JavaFX `PixelBuffer` javadoc (openjfx.io) and the foojay article "Tips on High Performance
  Rendering in JavaFX": the BufferedImage/PixelBuffer shared-`int[]` technique. The API is
  public; our code is written independently.
- PDFViewFX / GemsFX (Apache 2.0): looked at for feature comparison only.
