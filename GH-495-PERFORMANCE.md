# GH-495 — Parser performance investigation

Branch: `GH-495.performance.branch`

Goal: analyze the NIO object parser (`org.icepdf.core.util.parser.object`) and the
multi-threaded content parser (`org.icepdf.core.util.parser.content`) for design,
optimization, and thread-synchronization issues — the suspicion being that the
locking was over-zealous and possibly incorrect.

## TL;DR

- We **measured before optimizing** and the data repeatedly contradicted the framing:
  the object/content parser and its locking are **not** the bottleneck.
- The real costs are downstream — **text-extraction allocation, font derivation, and
  image-decode CPU** — and scaling is capped by **GC pressure and per-page load
  imbalance**, not lock contention.
- The largest single win was **rectangle clip handling** (avoiding `java.awt.geom.Area`
  boolean ops for axis-aligned clips): ~56% faster serial / ~70% faster parallel render on
  a vector/clip-heavy magazine. Plus a per-glyph allocation win, a memory win, latent
  correctness fixes, image-decode dedup, and a reusable benchmark/profiling harness.

## What shipped

| Commit | Change | Effect |
|--------|--------|--------|
| `a042a2a3d` | Guard null unicode-cmap / UCS2 lookup in substituted CID fonts (`ZFontType2.getCharToGid`) | Fixes a deterministic NPE that silently dropped text ("Error parsing text block") |
| `81b8aa6d6`, `71e540236` | `ParsingBenchmark` harness + `:core:core-awt:parsingBenchmark` task; forks one JVM per file; JFR cpu+alloc+gc+locks recorder | Trustworthy, repeatable measurement |
| `e1ddbd363` | Size decode buffer to the raw stream length (`Stream.getDecodedStreamBytes`) | −73% large (outside-TLAB) decode allocations; lower peak footprint for big streams (helps the 195 MB OOM case). Throughput unchanged. |
| `06328f2e7` | Transform glyph bounds via 4 corners instead of building a `Path2D` per glyph (`GlyphText.normalizeToUserSpace`) | **−74% `double[]`, −16% GC, −5..13% serial work** on text-heavy docs. The headline win. |
| `654394b3a` | Remove a discarded double `deriveFont` per `Tf` and a redundant CTM copy | Removes provably-dead allocation; wall-clock effect within noise |
| `e75c12bbd` | Index gray image bytes directly instead of a per-pixel synchronized `ByteArrayInputStream` read (`ImageUtility.copyDecodedStreamBytesIntoGray`) | ~7% on a CMYK+gray image-heavy page render (larger for pure grayscale); removes a per-pixel synchronized call |
| `593ee761b` | Resolve the object once in `Library.getObject(Reference)` instead of twice | Removes a redundant cache/state-manager lookup on a hot path |
| `008126857` | Make the shared `Dictionary.inited` flag `volatile` and delete the subclass fields that shadowed it (`Font`, `Indexed`, `Form`, `ShadingPattern`, `TilingPattern`, `PageTree`, `Destination`) | Unsafe-publication fix: a thread seeing `inited==true` now sees the fields `init()` wrote, for objects several page threads init concurrently |
| `3b61f3a10` | De-duplicate concurrent decodes of the same image (`ImagePool` in-flight tracking + `ImageReference.submitDecode`) | The same image referenced from multiple `Do`s / pages now decodes once instead of racing |
| `e6b02eb85`, `c35d97ee4` | Optional eager parallel image pre-decode at page init (`Resources.preLoadImages`), **default off** | Opt-in (`org.icepdf.core.imageReference.eagerDecode`) for image-heavy workloads; see Image loading below |
GH| `281c6b141` | Read the document buffer lock-free during object parsing (per-thread `ByteBuffer.duplicate()`; drop the `ObjectLoader`/`mappedFileByteBufferLock` locks; make `ObjectStream`/`CrossReferenceBase` independently thread-safe) | The #1 contended monitor eliminated: `ObjectLoader` enters 2600→5. Pixel-identical + race-free across 5×8-thread rounds. Wall-clock core-bound on 4 cores; scales with core count |
| `c8b8dfc90` | Decrypt without serializing on the shared `StandardEncryption` (derive the per-object key locally, drop the `synchronized` and its 1-entry cache) | Parallel decryption; `StandardEncryption` monitor contention 194→5 events. Pixel-identical (40/40), encryption test passes. Wall-clock within noise on 4 cores, scales with core count |
| `e27cdc3e7` | Rectangle fast-path for clip handling (`GraphicsState`): keep an axis-aligned clip as a `Rectangle2D` and intersect/transform it directly, only promoting to `Area` for non-rectangular clips | **−56% serial / −70% parallel render** on a vector/clip-heavy magazine (1.69x→2.51x); also lower peak memory. Pixel-identical output verified. **The biggest single win.** |
| _(uncommitted)_ | Zero-copy stream construction: the object parser hands each `Stream` (and `ImageStream`/`Form`/`TilingPattern`/`ObjectStream`/`CrossReferenceStream`) a read-only `ByteBuffer` view ("view mode", `ByteBufferBackedInputStream`) into the shared document buffer instead of bulk-copying the still-compressed bytes into a `byte[]` (`ObjectFactory`). Decode runs from a private `duplicate()`; the `byte[]` is materialized lazily only on the cold `getRawBytes()` save/sign/thumbnail paths. Plus `Stream.disposeDecompressed()`: page content streams drop their inflated buffer once the `Shapes` are built (re-derivable from the still-compressed view), edited streams skipped | Removes the per-stream compressed-bytes copy at parse time and the steady-state retention of inflated content-stream buffers; lower peak/steady heap. Decode output identical (covered by new tests). Encryption unaffected (decrypt runs on the `InputStream`) |

All changes pass the core suite plus the search/redaction tests (which depend on glyph
selection bounds). The zero-copy/view-mode change above adds two unit suites:
`ByteBufferBackedInputStreamTest` (unsigned-read + slice-window correctness of the adapter)
and `StreamViewModeTest` (view-mode decode is byte-identical to byte[]-mode, raw bytes
materialize lazily to exactly the slice window, and `disposeDecompressed()` allows a
re-decode while preserving edited streams).

## How we measured

`./gradlew :core:core-awt:parsingBenchmark -Picepdf.benchmark.dir=<corpus> -Picepdf.benchmark.heap=6g`

Per file it times: cold open (`Document.setFile`), a serial page-init sweep, and a
parallel page-init sweep (fresh `Document` per iteration so the object cache is cold).
Corpus: 7 PDFs from 3.8 MB to 26 MB (text-heavy, image-heavy, compressed-xref,
single-page), under `core/core-awt/src/test/resources/benchmark/`. JFR recordings under
`core/core-awt/build/benchmark/jfr/`.

Two measurement lessons (each cost a wrong conclusion before being caught):

1. **Verify the control.** An NPE seen only "in parallel" was assumed to be a race; it
   was a deterministic null bug — serial threw the *same* count once we actually ran
   serial. Always measure the side you're treating as clean.
2. **Isolate per file.** Running several large PDFs in one JVM let earlier files' heap
   garbage distort later files (the same file swung 0.4x–2.7x by run order). The harness
   now forks a JVM per file. **Trust serial time** (single-thread = real work); the
   per-run parallel number is noisy (±, ~0.5x swings) and should be averaged.

## What the profiler actually showed

Clean per-file speedup on 8 threads: single-page files ~1.0x (nothing to parallelize),
multi-page 1.65x–2.71x. The gap to linear is **not** locks:

- **Lock contention is minor.** On the image-heavy file, 80 total monitor-enter events.
  On the parse-heavy/encrypted file the top monitor (`ObjectLoader`) accounts for ~4 s of
  aggregate blocked time — but removing that lock alone moved the file 2.66x→2.73x
  (noise), because the freed parallelism is immediately reabsorbed by **GC**. Lock
  removal and allocation reduction only pay off together.
- **CPU** goes to image decode (YCbCr/ColorModel/IndexColorModel/FaxDecoder) + `Area`
  geometry on image docs, and `Lexer` + `drawString` + per-glyph layout on text docs.
- **Allocation/GC** is the shared ceiling. The dominant allocators are per-glyph
  geometry (`GlyphText`/`TextSprite`/`WordText` → `AffineTransform`/`Rectangle2D`/
  `double[]`) and font derivation — i.e. text extraction, not the parser.
- **Load imbalance**: large idle/parked time at the tail of each parallel sweep
  (coarse per-page granularity).

## Render path (`paint=true`)

Measured full render (init + paint to a `BufferedImage` at zoom 1.0), ≤50 pages/file:

| file | pages | serial | parallel | speedup |
|------|-------|--------|----------|---------|
| 2.pdf | 1 | 9332 ms | 8576 | 1.09x |
| mapping3 | 1 | 4331 | 4271 | 1.01x |
| NY_Central_Park | 1 | 2567 | 2591 | 0.99x |
| 2005CAT | 17 | 2131 | 1279 | 1.67x |
| 9216 | 6 | 1742 | 882 | 1.98x |
| PDF-1113 | 50 | 648 | 201 | **3.23x** |
| r01 | 50 | 732 | 229 | **3.20x** |

- **Render threading is healthy.** Multi-page documents parallelize *better* than init
  (~3.2x vs ~2.5x on 8 threads): the added rasterization is CPU-bound work with
  near-zero lock contention and low GC.
- **The cost is per-page image-pipeline CPU**, and **single large image pages are the
  real latency** (e.g. `2.pdf` = one page, 9.3 s) — unparallelizable at page
  granularity. JFR of that page: 0 monitor-enters, ~2% GC; time is in `YCCKRasterOp`
  (CMYK→RGB), gray-byte copying, soft-mask, and Marlin vector fill.
- `YCCKRasterOp.filter` already works on the raster `byte[]` with a repeated-pixel cache
  — the per-pixel CMYK math is essentially irreducible. The one clean win was the gray
  copy (`e75c12bbd`). A bigger render lever would be intra-page / image-decode
  parallelism (complex, not pursued).

## Image loading (`graphics.images.references`)

Image decoding was deferred until the single-threaded content parser reached each
image's `Do` operator, so decodes started one at a time as parsing progressed. Two
changes came out of looking at this:

- **In-flight decode de-duplication** (`3b61f3a10`) — `ImagePool` only cached *completed*
  images, so two references to the same image XObject that both missed the cache (the
  same image on several pages, or an eager pre-decode racing the parser) each started
  their own decode. It now tracks in-flight decodes by reference so the second joins the
  first. This is the lasting value from this thread and stands on its own.
- **Eager parallel pre-decode at page init** (`e6b02eb85`), **defaulted off**
  (`c35d97ee4`). `Page.init` can enumerate the page's image XObjects and kick off their
  decodes up front so they run in parallel while content parsing proceeds; the parser's
  own reference then joins the pooled/in-flight result. Image masks are skipped (their
  decode depends on the parse-time fill colour).

A/B across three deliberately different documents showed why it's **opt-in, not default**:

| doc | character | eager on vs off |
|-----|-----------|-----------------|
| Honda XR 650 (6 pg) | CID/text, cheap images | small **consistent penalty** (~5% serial) |
| 2005CAT (17 pg) | many images, reused across pages | ~5%, within noise |
| photograph-issue-2 (50 MB, 50 pg) | image-heavy | runs **contradicted** each other; benefit lost in noise |

The benefit is marginal even on the image-heavy doc because the parallel render already
saturates the CPU with per-page threads — there are no spare cores for pre-warm to use.
It only helps the serial render of a single image-heavy page, and even there it's small.
Meanwhile it adds a small upfront cost (resolving every image XObject + queueing decodes)
that the common text/vector case doesn't recoup. Investigating mask coverage turned out
to be a red herring: 2005CAT has ~no masks (1 image-mask, 0 explicit `/Mask` across 298
images); its "skipped" images were Form XObjects and **already-pooled images reused
across pages**.

Takeaway: image decode is rarely the multi-threaded bottleneck here — CPU saturation and
per-page work are.

## Other hotspots surfaced but not addressed

These showed up in the profiles and are worth recording even though they weren't tackled:

- **`java.awt.geom.Area` clip operations — DONE (`e27cdc3e7`), and the biggest win.** On
  the vector/clip-heavy image doc (2005CAT), `sun.awt.geom` Area math (`Order`/`Curve`/
  `AreaOp`/`Edge`) was ~40% of render CPU (the `Math.acos` leaf samples live in here too —
  it's JDK curve math, not our code). `GraphicsState` kept the clip as an `Area` and did an
  Area boolean **intersect** on every clip operator (allocating the `Area` ~3×) plus an
  Area **transform** on every CTM change. The rectangle fast-path keeps an axis-aligned
  clip as a `Rectangle2D` and intersects/transforms it directly, promoting to `Area` only
  for genuinely non-rectangular clips or rotated/sheared transforms. Result: 2005CAT render
  −56% serial / −70% parallel, pixel-identical output verified on 47 pages across two docs.
- **Marlin path rasterization** (`sun.java2d.marlin.DRenderer`) — significant on
  vector-heavy pages, but it's the JDK filling complex paths; not ours to optimize.
- **Lexer number autoboxing** — the content/object lexers allocate `Integer`/`Float` per
  numeric token (a few thousand allocation samples on the text doc). Inherent to the
  token model; a primitive-token path would cut it but is invasive.
- **`StandardEncryption` lock** — DONE (`c8b8dfc90`): decryption was `synchronized` only to
  guard a 1-entry per-object key cache; made the key derivation local and removed the lock
  (monitor contention 194→5 events, pixel-identical output). With decrypt no longer
  serializing, `ObjectLoader` is now clearly the #1 contended monitor (650 enter events on
  r01) — the next target (see the shelved buffer-lock removal under "Deliberately not done").

## Hardware context (important for reading the numbers)

All numbers above were measured on a **4-core / 8-thread i7-7700K (2017)**, with the
benchmark running `threads=8` — i.e. **8 worker threads on 4 physical cores**. That
shapes several conclusions and they would likely change on newer, higher-core hardware:

- **Per-page parallel speedups would go higher.** Capping at ~2.5–3.2x is partly because
  there are only 4 real cores; hyperthreads share execution units, so CPU-bound render
  work doesn't scale much past 4. More physical cores → more headroom.
- **Lock contention would matter *more*, not less.** Contended monitors (`ObjectLoader`,
  font init, `StandardEncryption`) get worse with more concurrent threads, so the
  `volatile`/dedup fixes gain value and the case for the shelved buffer-lock removal
  strengthens (still only paired with allocation reduction).
- **GC could become a sharper ceiling** with more allocating threads — raising the value
  of the allocation work (GlyphText).
- **Eager image pre-decode could actually pay off** on a many-core box, or in real
  interactive single-page viewing (3 idle cores on this same 7700K) — which is exactly
  why it's kept as an opt-in flag rather than removed. The benchmark (all pages,
  `threads=#logical`) is a worst case for it.

In short: the *relative* findings (where time goes, which fixes are real wins) hold; the
*absolute* speedup ceilings and the lock-vs-GC balance would shift with more real cores.

## Deliberately not done (with rationale)

- **Object-buffer lock removal** — DONE (`281c6b141`). Re-evaluated after the clip fix and
  the encryption-lock removal exposed `ObjectLoader` as the clear #1 monitor; landed it
  with thorough parallel-correctness verification. The earlier "do not land alone" caveat
  was about GC reabsorption, but the real reason it shows no wall-clock win on this box is
  **core saturation** (8 threads on 4 cores) — the contention is gone (2600→5) and the
  benefit scales with physical cores. The read-only/duplicate invariant is documented on
  `Library.getMappedFileByteBuffer()`.
- **Further allocation micro-opts** (font `deriveFont` caching, `GraphicsState`
  transforms): diminishing, noise-level returns on the init path.
H- **Base/mask decode split** (pre-decode the colour-space-independent base, apply masking
  at parse time so masked images could be pre-warmed): not built. The masked set on the
  test corpus was negligible (red herring above), and `FaxDecoder`/`RawDecoder` bake the
  fill colour into decode, so a clean split would need decoder-level changes for little
  gain.
- **Synchronizing the non-`synchronized` `init()` methods**: audited and not needed. The
  expensive, shared inits (all concrete fonts, colour spaces, patterns, forms, catalog,
  page tree) are already `synchronized`; `SimpleFont`/`CompositeFont` are only reached via
  synchronized concrete subclasses, and the remaining non-synchronized inits are
  document-open singletons or per-page annotations (low concurrency exposure).

## Suggested next steps (bigger levers, separate efforts)

1. **Intra-page / image-decode parallelism**: the render path already parallelizes well
   across pages (~3.2x), so the remaining render latency is single large image pages
   (e.g. 9.3 s for one page). Splitting large image decode/conversion across threads is
   the only way to cut that, since page-granularity parallelism can't help — complex,
   image-pipeline work.
2. **Per-page load imbalance**: viewer-level scheduling (finer granularity / work
   stealing) to reduce the tail-idle time, independent of the parser.
3. If pursuing throughput on parse-heavy/encrypted docs: pair the shelved buffer-lock
   removal with the allocation reductions, and look at the `StandardEncryption` and
   shared font-init (`Type0Font`) locks.