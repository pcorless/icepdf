# Design Note: Buffer-Free Transparency-Group Blending

**Status:** draft / proposal — Phase 1 (de-fuzz classifier) implemented; Phase 2 not started.
**Context:** GH-495 performance work. Follow-up to the oversized-group fix in
`9c65cbf8c` (scale the offscreen buffer instead of dropping the blend).

## 1. Problem

Every non-trivial transparency group is rasterised into its own
`BufferedImage` back buffer before being composited onto the page
(`FormDrawCmd.createBufferXObject` → `g.drawImage(xFormBuffer, …)`). Allocating
and painting these ARGB buffers is the dominant cost of pages that lean on
transparency groups, and the buffer size is what forced `MAX_IMAGE_SIZE`
clamping (and the white-background bug that motivated this note).

For a large class of real-world groups the buffer is unnecessary: the group can
be painted directly onto the page with a Java2D `Composite` set on the
`Graphics2D`, because the page raster already *is* the backdrop the blend math
needs. The goal is to take that buffer-free fast path when it is provably
equivalent, and keep the buffer path only where the semantics genuinely require
it.

## 2. Current architecture (as built)

- `AbstractContentParser.consume_Do` decides, per `Do`, whether a form becomes
  a `FormDrawCmd` (buffered) or an inline `ShapesDrawCmd`. The predicate is the
  recently-reworked block around the `withinMaxSize` / `hasGroupEffect` /
  `oversizedBlendOnly` locals.
- `FormDrawCmd.paintOperand` rasterises the group (`createBufferXObject`),
  optionally applies soft masks (`applyMask`), then `g.drawImage`s the buffer.
  - **`createBufferXObject` is a shared path.** It builds *both* the main group
    buffer and the `isMask`=true soft-mask/outline sub-buffers. The
    oversized-group down-scaling from `9c65cbf8c` is therefore deliberately
    scoped to `!isMask` only — the mask sub-buffers keep the original
    clamp-to-main-buffer sizing. The first cut of the fix scaled the mask
    sub-buffers too and silently regressed every SMask document in the corpus
    (e.g. `InterponSpecManual.pdf` cover banner washed out, lum 119→156). Any
    redesign touching this method must hold the mask path invariant and
    regression-test the *full* Multiply corpus, not just the target file.
  - **`MAX_SCALED_FORM_SIZE` (default 16384, sysprop
    `org.icepdf.core.maxScaledFormSize`) gates the down-scale.** Forms whose
    bbox exceeds it stay inline rather than scaling. This exists to exclude
    `±Short.MAX_VALUE` (32767) "unbounded" sentinel bboxes — content that is
    actually small but declares a page-sized/infinite box and is clipped
    elsewhere. Scaling those to fit `MAX_IMAGE_SIZE` collapses the real content
    to ~0.03× and destroys it. `1.pdf` (~9455 wide) passes the gate; sentinels
    do not.
- `BlendComposite` is already a `java.awt.Composite` implementing the separable
  PDF blend modes; `BlendCompositeDrawCmd` sets it on the `Graphics2D`.
- Per-object draw commands (`commonFill`/`commonStroke` → `setAlpha`,
  `ColorDrawCmd`, `ImageDrawCmd`) set their own `AlphaComposite` on `g`.

**Where the blend actually comes from (important, and easy to get wrong).**
`FormDrawCmd` does *not* apply the blend mode itself — `paintOperand` ends in a
plain `g.drawImage(buffer, …)`. The blend is set on `g` *externally, before the
`Do`*, by the page-level command stream:

- `/GS2 gs` → `consume_gs` concatenates the ExtGState, then calls `setAlpha`,
  which (for a non-Normal blend) emits a **`BlendCompositeDrawCmd(Multiply, …)`
  onto the page-level `shapes`**.
- `/Fm0 Do` → `consume_Do` routes the group to `FormDrawCmd`.
- At paint time the page stack runs that `BlendCompositeDrawCmd` first (setting
  `g`'s composite to `BlendComposite(Multiply)`), *then* `FormDrawCmd` draws its
  buffer — so the single `drawImage` composites under the already-active blend.

**The buffer's real job is therefore isolation, not blending.** The group's
inner objects paint into the offscreen canvas, so the `AlphaDrawCmd`s baked into
the form's shapes reset the *canvas* composite, not the page's. The page's
`BlendComposite` survives untouched until the buffer is drawn as a unit. This is
precisely why the buffered path is correct without any blend-aware machinery.

**Key constraint discovered empirically:** removing the buffer naively does
*not* work. Dropping the group to an inline `ShapesDrawCmd` (even with the
external `BlendCompositeDrawCmd` still in front of it) loses the blend, because
the group's inner draw commands each call `setAlpha(... ALPHA_FILL)` which
overwrites `g`'s composite back to `SRC_OVER` before the first object paints.
Crucially, **those inner `AlphaDrawCmd`s are baked into `formXObject.getShapes()`
during `Form.init()` — a separate `ContentParser` pass at form scope — so they
are already materialised by the time `consume_Do` runs.** Any buffer-free path
must therefore make the inner composite ops *blend-aware*; it cannot fix this by
merely (re-)setting a composite around the `ShapesDrawCmd` at `Do` time.

## 3. Classification: which groups can skip the buffer

A group can be painted inline, buffer-free, **iff all** of the following hold:

| Condition | Why a buffer is otherwise required |
|-----------|-----------------------------------|
| No soft mask (`SMask == null`, both form and gs ExtGState) | Luminosity/alpha mask must be read back from a raster |
| Not knockout (`/K false`) | Knockout composes each element against the group's *initial* backdrop, not the running result — no `Graphics2D` equivalent |
| Not isolated (`/I false`) | Isolated groups composite against a transparent backdrop, then onto the page as a unit |
| Group constant alpha `ca == 1` | A group-level `ca` applies to the *composited* group, which requires compositing it first |
| Blend mode is separable (Normal, Multiply, Screen, Overlay, Darken, Lighten, ColorDodge, ColorBurn, HardLight, SoftLight, Difference, Exclusion) | Non-separable modes (Hue/Sat/Color/Luminosity) can still be a custom `Composite`, but are higher-risk; defer |
| Inner content is non-overlapping / single-layer | A buffer flattens inner Normal compositing before applying the group blend; painting the group blend per-element against the page double-applies it in overlap regions (see §4). Hard to decide cheaply — see Risks |

Our `1.pdf` group (`/I false /K false`, no SMask, `ca 1`, `/BM Multiply`, 49
side-by-side non-overlapping images each forced to `/BM Normal` by an inner
`/GS0 gs`) satisfies all of these — it is the canonical fast-path case.

Proposed predicate (new helper, e.g. `Form.isInlineBlendable()` or a static in
`consume_Do`):

```
isInlineBlendable =
       extGState != null
    && extGState.getSMask() == null
    && !form.isKnockOut()
    && !form.isIsolated()
    && extGState.getNonStrokingAlphConstant() == 1
    && isSeparableBlend(extGState.getBlendingMode());   // Normal counts as trivially blendable
```

(`isIsolated`/`isKnockOut` are already plumbed onto the form's graphics state in
`consume_Do`.)

## 4. The blend-aware composite change

This is the crux of the refactor, and the part most easily underestimated.

The page-level `BlendCompositeDrawCmd` that `consume_gs` already emits (see §2)
is *not* the problem and does not need to be re-emitted — it correctly sets the
group blend on `g` before the `Do`. The problem is that the group's **inner**
draw commands immediately overwrite it: each `setAlpha(... ALPHA_FILL)` builds an
`AlphaComposite.getInstance(rule, alpha)` and sets it on `g`, resetting the
composite to `SRC_OVER` before the first object paints. To paint a group inline
under blend mode *M*, every inner paint op must instead compose with *M*
(carrying the per-object alpha).

**Two architectural facts that constrain the fix (verify before coding):**

1. **There is no `GraphicsState` at paint time.** `Shapes.paint` threads only
   `g`, the page, clip/base transforms and a timer to each `paintOperand`. The
   active composite lives solely on `g`. `AlphaDrawCmd` holds a *prebuilt*
   `AlphaComposite` and does a bare `g.setComposite(it)`. So the blend decision
   cannot be made at paint time by "consulting state" — it must be baked into the
   command (its composite) at *parse* time, or the paint signature must grow new
   state. The former is far less invasive.

2. **Parse-time `setAlpha` already branches on blend mode.** The 3-arg
   `setAlpha` (the `AlphaPaintType` overload) *already* emits a
   `BlendCompositeDrawCmd` instead of an `AlphaDrawCmd` when
   `extGState.getBlendingMode()` is non-Normal. And the form is parsed
   (`Form.init`) with `xformGraphicsState`, a copy of the page state that *already
   carries the `gs` blend*. So in principle the form's own inner objects could
   inherit the group blend through the state that is already plumbed in.

**Why the inner objects lose the blend today (traced on `1.pdf`).** The cause is
(1) above, confirmed concretely:

- Page stream: `… /GS2 gs /Fm0 Do …`, where `GS2` is `/BM /Multiply`. This
  emits the page-level `BlendCompositeDrawCmd(Multiply)`.
- Form `Fm0`'s *own* content wraps every one of its 49 images in
  `q /GS0 gs <cm> /ImN Do Q`, and **`GS0` is `/BM /Normal`**. So when the form
  is parsed, `consume_gs(/GS0)` resets the form's graphics-state blend to Normal;
  the non-Normal branch of `setAlpha` is *not* taken; each image compiles to an
  `AlphaDrawCmd(SRC_OVER)`. `formXObject.getShapes()` therefore contains **no**
  blend command at all.
- Inline, the first such `AlphaDrawCmd(SRC_OVER)` does `g.setComposite(SRC_OVER)`
  and wipes the page's Multiply before any image draws → opaque white. The buffer
  path survives only because those `SRC_OVER` ops act on the offscreen canvas, not
  on `g`, which still carries the page Multiply when the buffer is drawn as a unit.

So the inherited-blend idea in (2) does **not** hold for this file: the form
deliberately overrides the blend to Normal internally. The group's Multiply is a
property of the *group as a unit* (`GS2` around the `Do`), not of the inner
objects — which is exactly what the buffer captures.

**Consequence — the inline fast path is only valid when the group flattens
trivially.** Replacing the inner `SRC_OVER` ops with the group's Multiply
reproduces the buffered result **only if inner elements do not overlap** (and
carry no inner alpha/blend of their own). `1.pdf` satisfies this — the 49 images
are placed side-by-side, no overlap — so "Multiply each image against the page"
equals "Normal-composite into a buffer, then Multiply the buffer." With
overlapping inner elements the two diverge (the overlap region would be
multiplied twice), so such groups must stay buffered. This is a real addition to
the §3 predicate: *inner content must be non-overlapping / single-layer*, which
is not cheaply decidable up front — see Risks.

The two candidate shapes for the (valid, trivially-flattening) case:

- **(a) Make the inherited group blend reach every inner composite op at parse
  time** — i.e. ensure the form's `setAlpha`/image paths construct a
  `BlendComposite` (mode + per-object alpha) rather than an `AlphaComposite`
  whenever a group blend is active in the form's `GraphicsState`. Baked at parse
  time, so paint stays state-free. `BlendComposite` already accepts an alpha arg.
- **(b) Rewrite at `Do` time** — walk the form's `ShapesDrawCmd` and swap each
  `AlphaDrawCmd` for a blend-carrying equivalent. **Risky:** forms are shared,
  pooled resources (`Form` is a cached `Resources` entry, init is `synchronized`
  because multiple page threads touch it), so mutating the baked shapes corrupts
  any other use of the same form under a different (or no) blend. Only viable if
  the shapes are cloned first.

Net effect of a correct (a): inner objects composite `srcOver`-with-blend against
the page, which for a non-isolated/non-knockout group **with non-overlapping
inner content** is the PDF spec result, with zero back buffer. Overlapping or
multi-layer groups fall back to the buffer.

> Alternative considered: a stack-based `Composite` that wraps whatever rule the
> inner op asks for, threaded through a widened `paintOperand` signature.
> Rejected as more invasive than baking the blend into the inner commands at
> parse time — but it is the fallback if parse-time baking proves infeasible.

## 5. Phasing

- **Phase 1 — DONE (de-fuzz, no behaviour change).** The original plan here was
  "route `Normal`/`ca==1`/no-SMask groups inline." Tracing the live code showed
  that was already a **no-op**: those groups never reached the buffer in the
  first place. The routing gate was never an "is this a transparency group?"
  test — it is effectively a *"does this group need an offscreen buffer?"* test,
  and a Normal/opaque/no-mask group already failed it (`hasGroupEffect == false`)
  and fell to the inline `ShapesDrawCmd`. So Phase 1 instead became a
  **behaviour-preserving refactor**: the tangled
  `withinMaxSize`/`hasGroupEffect`/`oversizedBlendOnly` booleans were collapsed
  into a single documented predicate `AbstractContentParser.requiresOffscreenBuffer(Form)`
  (buffer iff a group effect — SMask / non-Normal blend / `ca ∈ (0,1)` — is
  present *and* the group fits the buffer budget). Validated **byte-identical**
  across the 21-doc Multiply corpus and the core test suite. This locks current
  behaviour behind a readable decision and is the seam Phase 2 edits.
  - **Notable gap surfaced:** isolation/knockout (`/I`, `/K`) are read into the
    graphics state (`consume_Do` → `setIsolated`/`setKnockOut`) but are **not
    consulted by the routing predicate** — see §5.2.
- **Phase 2:** extend the inline path to separable non-Normal blends (Multiply
  et al.) via the blend-aware `setAlpha`. This is where `1.pdf`-style groups stop
  needing a buffer at all (and the scale-down quality loss from `9c65cbf8c`
  disappears for them). Gated by the §4 non-overlap constraint.
- **Phase 3 (optional):** non-separable blends as custom `Composite`s.
- Buffer path remains the fallback for SMask / knockout / isolated / group-`ca`.

## 5.2 Isolation/knockout-aware routing (prototype)

A transparency group is defined by four attributes: soft mask, blend mode,
constant alpha, and the **isolation (`/I`) / knockout (`/K`)** flags. The Phase 1
predicate (`requiresOffscreenBuffer`) consults only the first three. `/I` and
`/K` are parsed (`consume_Do` → `setIsolated`/`setKnockOut`) but **ignored when
choosing inline vs buffer.** This is a real correctness gap, not just untidiness:

- **Isolated group:** its elements must composite against a *transparent*
  backdrop, then the result composites onto the page as a unit. Painted inline,
  any inner blend/partial-alpha element instead composites against the *page* —
  the wrong backdrop. Only a buffer provides the transparent backdrop.
- **Knockout group:** each element composites against the group's *initial*
  backdrop rather than the running result — there is no `Graphics2D` equivalent,
  so it needs a buffer (today it is approximated by forcing `AlphaComposite.SRC`
  in `commonFill`, which is not the same thing).

**But isolation/knockout only changes the result when inner content actually has
transparency** (a blend or `ca < 1`). A fully-opaque, all-Normal isolated group
renders identically inline, so blindly buffering every `/I`/`/K` group would
re-introduce the buffer cost (and the §5.1 scale-down blur) for groups that
don't need it. The prototype therefore measures impact before committing to a
rule, and the eventual predicate should be *isolation/knockout AND the group has
inner transparency* — the latter being the part that needs cheap detection (same
problem as the §4 non-overlap test).

Prototype approach: extend `requiresOffscreenBuffer` to treat `/I`/`/K` as a
buffer-requiring signal (behind a measurement flag), render the corpus, and
diff against the Phase 1 baseline to see which documents contain such groups and
whether buffering them changes — and improves — the output.

**Prototype result (implemented, flag `org.icepdf.core.isolationAwareRouting`,
default off).** `requiresOffscreenBuffer` now ORs in
`form.isIsolated() || form.isKnockOut()` when the flag is set. Corpus impact:
- Flag-on render is **byte-identical** to the Phase 1 baseline across all 21
  docs — so the change is *safe* (no regression), and core tests stay green.
- Instrumented count: the entire corpus contains **exactly one** isolated/knockout
  group — a knockout group with `ca 0.5` — which is *already* buffered via its
  partial alpha. **Zero** isolation/knockout-only groups exist, so there is
  nothing for the new signal to flip.

Conclusion: isolation/knockout-only groups (the case this fix targets) are **not
exercised by the Multiply corpus**; it can confirm safety but cannot prove the
correctness improvement. Validating that requires a **synthetic fixture**: an
isolated group whose *inner* content uses a blend or `ca < 1` but whose group
ExtGState has no effect — inline would composite the inner blend against the page
(wrong backdrop), buffered against transparent (correct).

### 5.2.1 Fixture result — exposes a deeper blend bug

Built that fixture (`iso_fixture.pdf`: cyan page; isolated `/I true` group, Normal
group ExtGState; inner gray rect under `/BM Multiply`). Center-pixel results:

| Path | center pixel | meaning |
|------|--------------|---------|
| Spec-correct isolated | `#808080` gray | Multiply vs *transparent* backdrop → source colour |
| Routing OFF (inline, today) | `#007f7f` teal | `gray×cyan` — inner Multiply hit the **page** (non-isolated) |
| Routing ON (buffered) | `#000000` **black** | wrong — *not* gray |

So **two** bugs, not one:

1. **Routing ignores `/I`.** Inline composites the inner blend against the page
   backdrop — the non-isolated result. This is the gap §5.2 set out to close.
2. **`BlendComposite` ignores a transparent *backdrop*.** Each separable
   blender special-cases a transparent *source* (`if (src[3]==0) return dst;`)
   but never a transparent *backdrop* (`dst[3]==0`). For Multiply against the
   isolated group's transparent buffer: colour `= src×0 = 0` (black), alpha
   `= src+0 = 255` (opaque) → **opaque black** instead of the source colour the
   spec requires (a separable blend over a fully-transparent backdrop = `Cs`).

Bug 2 is why simply turning isolation-aware routing on does **not** yield the
correct isolated render — it swaps the wrong-teal for wrong-black. It also
**explains the `createBufferXObject` white-fill hack**: pre-filling the buffer
white gives Multiply a `255` backdrop (`src×255 ≈ src`), masking bug 2 — but only
for *group-level* non-Normal blends over additive colour spaces, which is why an
*inner*-blend isolated group (this fixture) falls through to black.

**Implication for the redesign.** The natural fix is in the blender: make each
separable blender weight by backdrop alpha (at minimum `if (dst[3]==0) return
src;`, ideally the full `Cr = (1-αb)·Cs + αb·B(Cb,Cs)` interpolation). On the
fixture this is exactly right — flag-on + the guard renders **`#808080` gray**.

### 5.2.2 …but a *global* backdrop-alpha fix regresses the corpus (tried, reverted)

Applying that guard once in `BlendComposite.compose` (`result = dstPixel[3]==0 ?
srcPixel : blender.blend(...)`) fixed the fixture but **changed 13 of 21 corpus
docs**, and at least one is a clear **regression**: `InterponSpecManual.pdf`
grows opaque **black rectangular blocks** over a photographic collage (meanΔ ≈
182 across ~8 % of pixels; diff heat-map localises it to the photo region). So
the change was reverted — corpus is back to 21/21 identical.

Why it regresses: `BlendComposite` is **shared across far more than
isolated-group buffers** — soft-mask rasters, image-group buffers, and inline
page blends all run through the same `compose`. In several of those the
`dst[3]==0` pixels are *expected* to stay transparent/black (e.g. an image
composited into a group buffer, later masked), and "return source" turns them
into opaque source colour. The white-fill hack and the current black behaviour
are **load-bearing** in those paths in ways the corpus depends on.

**Revised conclusion.** Bug 2 is real and is the core of the fuzzy blending, but
it **cannot be fixed with a single global guard in `compose`.** The fix must be
*scoped* to the case that needs it — i.e. only when compositing an **isolated
group's** content against its own (genuinely transparent) backdrop, not for every
`BlendComposite` use. Options, to be designed/validated next:

- carry an "isolated-group backdrop" flag into the `BlendComposite` instance used
  for that group's buffer, and apply the `dst[3]==0 → src` rule only there; or
- give isolated-group buffers the same explicit backdrop initialisation the
  white-fill provides, generalised beyond additive-CS Multiply (a correct
  transparent-backdrop init rather than a per-mode hack); or
- rework the group buffer compositing to track αb properly (the real model),
  which subsumes both and lets the white-fill be deleted — biggest change.

Each must be gated against the full corpus (especially the 13 docs that moved
here) **and** the fixture (center must reach `#808080` without re-breaking
`InterponSpecManual`).

Fixture and harness live in `core/core-awt/src/test/resources/blending/`
(generator `make_iso_fixture.py`; reference assertion once correctly scoped:
center `≈ #808080`, `InterponSpecManual` unchanged).

### 5.2.3 Scoped backdrop-alpha fix — implemented, corpus-neutral

Took the first option above and it works. A render-scoped
`ThreadLocal<Boolean> TRANSPARENT_BACKDROP` on `BlendComposite`, read **once per
`compose()`** (not per pixel), weights the blend by backdrop alpha when set —
`Cs' = (1-αb)·Cs + αb·B(Cb,Cs)` (αb=0 → source, αb=1 → full blend, partial →
interpolated, so anti-aliased edges and overlapping inner content are handled,
not just the binary `dst[3]==0` case). `FormDrawCmd` sets it (save/restore for
nesting) around **just the isolated group's main-buffer creation** — not the
mask/outline sub-buffers, not the shared soft-mask / image-group / inline-page
paths. Result:

| | center pixel | corpus vs Phase 1 |
|---|---|---|
| Fixture, routing on + scoped fix | **`#808080` gray** (correct) | — |
| Corpus, default flags | — | **21/21 identical** |
| Corpus, routing on | — | **21/21 identical** |

Core tests green. The corpus is untouched because it contains **no isolated
groups** that reach a buffer, so the flag is never set there — the fix is inert
everywhere except a genuinely-isolated buffered group.

Important subtlety on the gate: the buffer-creation call passes a parameter
*named* `isMask` that is actually `normalBM` for the main buffer, so an earlier
`!isMask` gate silently excluded the (Normal-group) fixture case. The flag is
therefore set at the **call site** of the main-buffer creation, where "this is
the group's own buffer" is unambiguous, rather than inside `createBufferXObject`.

**Status of the two knobs now:**
- *Scoped backdrop fix* — **always on**, unconditional, corpus-neutral. It
  corrects any *already-buffered* isolated group (one with an SMask/blend/`ca`
  that already routes to a buffer) for free; such a group previously blended its
  inner content against a black backdrop.
- *Isolation-aware routing* (§5.2) — still **off by default**. It is what buffers
  an *isolation-only* group (no other effect) so the scoped fix can apply; the
  fixture needs both. Held off pending the §5.2.1 "only when inner transparency"
  refinement (a blanket `/I`/`/K` → buffer over-buffers opaque isolated groups)
  and real-world isolated-group validation beyond the synthetic fixture.

### 5.2.4 Partial-αb generalisation done (scoped); white-fill deletion blocked

Two follow-ups were attempted:

1. **Generalise the scoped rule to partial αb — done, kept.** The binary
   `dst[3]==0 → src` became the full `Cs' = (1-αb)·Cs + αb·B` interpolation
   inside the `TRANSPARENT_BACKDROP` scope. Still isolated-only, so corpus stays
   21/21 identical and the fixture stays `#808080`; now also correct for
   anti-aliased / overlapping inner content rather than only fully-empty backdrop.

2. **Delete the white-fill hack by broadening the rule to *all* group buffers —
   attempted, reverted.** The white-fill is the additive-CS workaround for the
   *non-isolated* group buffer (which ICEpdf also rasterises against a transparent
   backdrop). The hope was that the αb-weighted blend would reproduce it
   (`αb-aware(transparent) = src` ≡ `Multiply(src, white) = src`) and the fill
   could go. Measured:
   - white-fill **on** + flag broadened to `isTransparencyGroup`: **6/21 changed**;
   - white-fill **off** + flag broadened: **10/21 changed**.

   So broadening to non-isolated groups is **not** corpus-neutral even with the
   fill still on — the αb interpolation differs from the plain blender at
   partial/transparent-backdrop pixels in real documents (overlap, AA edges,
   non-additive-CS groups the fill never covered). Without per-doc references
   these can't be declared improvements, and at least the global precedent
   (§5.2.2) shows some are regressions. Reverted to isolated-only.

   **Conclusion:** the white-fill hack **cannot be deleted** by this route while
   staying corpus-safe. Deleting it requires the *correct general* group-buffer
   compositing model (track αb properly across the whole buffer, including the
   buffer-over-page step and source alpha), validated per-doc against references —
   a real project, not a scoped flag. Until then the white-fill stays, and the
   αb-aware rule stays scoped to isolated groups, where it is provably neutral.

## 5.1 Interim scaling quality regression (largely addressed)

The `9c65cbf8c` fix restored correctness at the cost of resolution. The original
clamp capped the **largest single dimension** at `MAX_IMAGE_SIZE` (2000) and
scaled uniformly. For a high-aspect-ratio strip this was pathological: `1.pdf`'s
~9455×631 group became a **2001×134** buffer — the width hit the cap and dragged
the height down to 134 px, leaving almost no vertical detail in the scanned
line-art before it was stretched back to 631 units.

**Fixed (area budget).** The clamp now bounds the buffer by total pixel **area**,
preserving aspect: `scale = √(MAX_IMAGE_SIZE² / (w·h))`. This keeps the *same
peak memory* as a `MAX_IMAGE_SIZE` square (~16 MB) but distributes it by aspect,
so `1.pdf`'s strip becomes **~7742×517** — ~3.9× the height, dramatically more
detail (verified visually: crisp line-art vs the previous vertical smear). Impact
is surgical: on the Multiply corpus only `1.pdf` changes (26/27 byte-identical to
the fresh HEAD baseline), because it is the only doc with an oversized
high-aspect group. `MAX_IMAGE_SIZE` (sysprop `org.icepdf.core.maxSmaskImageSize`)
still tunes the budget, so raising it pushes toward full-res 1:1.

Remaining headroom, if ever needed (no longer urgent):

1. **Phase 2 makes it moot for the common case.** A non-isolated/non-knockout
   separable-blend group paints inline against the page raster with no buffer at
   all — full page resolution, no scale-down.
2. **Scale to device pixels, not group user-space.** Size the buffer to the
   group's footprint *after* the page CTM/zoom rather than the raw bbox; pairs
   well with the area budget for very large groups at modest zoom.
3. **Tile the group** into ≤`MAX_IMAGE_SIZE` strips composited in sequence —
   full resolution with bounded peak memory, for groups too large to buffer
   whole even under the area budget.

The test matrix's oversized-group row should assert an SSIM threshold against the
full-resolution (`maxSmaskImageSize=20000`) reference; the area-budget output is
now much closer to it than the old largest-dimension clamp.

## 6. Test matrix

A real-world corpus of transparency-heavy documents will back this (provided
separately) rather than only synthetic fixtures — each category below should map
to one or more documents from that suite. Capture a known-good reference render
per document **before** Phase 1 (the existing QA image-compare / SSIM harness),
then gate every phase against it so the buffered → inline migration is provably
non-regressing. The categories to ensure the corpus covers:

| Fixture | Group attrs | Expected path | Assertion |
|---------|-------------|---------------|-----------|
| Multiply line-art over colour (`1.pdf`-like) | `/I false /K false`, Multiply, ca 1 | inline (Phase 2) | colour shows through white; matches buffered render within SSIM threshold |
| Normal group, ca 1 | Normal | inline (Phase 1) | byte-identical to buffered |
| Group with `ca 0.5` | Normal, ca .5 | buffered | unchanged |
| Luminosity SMask group | SMask | buffered | unchanged |
| Knockout group | `/K true` | buffered | unchanged |
| Isolated group | `/I true` | buffered | unchanged |
| Oversized Multiply group (`> MAX_IMAGE_SIZE`) | Multiply | inline (Phase 2) | full-resolution, no scale-down blur; matches `maxSmaskImageSize=20000` |
| **Overlapping** inner elements under group Multiply | Multiply, inner overlap | buffered (overlap forbids inline) | no double-multiply in overlap; matches buffered render |
| Sentinel-bbox form (`±Short.MAX_VALUE`, small real content) | any | inline (gated by `MAX_SCALED_FORM_SIZE`) | content not collapsed; unchanged from buffered |
| Oversized group *with* SMask | SMask, big bbox | buffered, mask sub-buffer clamped (not scaled) | unchanged from pre-fix; corpus SMask docs identical |
| Non-separable (Luminosity BM) | Luminosity | buffered (until Phase 3) | unchanged |

Also: a perf assertion (buffer-allocation count or wall-time) on a
transparency-heavy page to confirm the win.

## 7. Risks / open questions

- **Composite reset discipline:** the whole approach hinges on every inner paint
  op going through `setAlpha`. Audit for any draw command that sets a composite
  directly and bypasses it.
- **Clipping:** the group BBox clip is applied inline today via `ShapeDrawCmd`;
  confirm it composes correctly with the blend (it should — clip is orthogonal
  to composite).
- **Nested groups:** an inline group containing a buffered child (or vice versa)
  — the active-blend state must save/restore correctly across nesting.
- **Shared `createBufferXObject` / mask-path invariant:** as noted in §2, this
  method services both the main buffer and the `isMask` mask/outline
  sub-buffers. The corpus already caught one regression here (mask sub-buffers
  scaled by mistake). Phase 2 removes the buffer for the *main* fast-path group
  but leaves the SMask/knockout/isolated groups on the buffer path — so the
  mask sub-buffer code stays live and the same invariant and corpus gate apply.
- **Sentinel-bbox forms:** `±Short.MAX_VALUE` bboxes must not be sized/scaled as
  if real. The buffer path guards this via `MAX_SCALED_FORM_SIZE`; an inline
  path must apply the analogous bound (or rely on the BBox clip) so it never
  tries to materialise an effectively-infinite group.
- **Overlap detection (the hard one):** the inline path is only spec-correct when
  inner elements don't overlap (§4). Deciding that cheaply is non-trivial — the
  form's shapes would have to be scanned for intersecting painted bounds before
  routing, and conservative bounds (e.g. image `cm` rectangles) can report
  overlap that isn't really there (transparent margins) or miss it (clipped
  content). Safe default: route to inline only when overlap is *provably* absent
  (e.g. all inner draws are images with disjoint device rectangles, as in
  `1.pdf`), and buffer everything else. Getting this wrong reintroduces the
  double-multiply artifact, so the corpus must include an overlapping-inner case.
- **`BlendComposite` correctness/perf:** it runs per-pixel in Java; for large
  fills it may be slower than a buffer + single `drawImage`. Measure; the inline
  win is allocation, not necessarily per-pixel throughput. May want a
  size heuristic that still buffers very large *simple* groups.
- Non-separable modes deliberately deferred.

## 8. Scope estimate

Phase 1 is **done** — it was a small behaviour-preserving de-fuzz
(`requiresOffscreenBuffer`), not the routing change originally imagined (which
the §5 trace showed was already the default). The §4 trace is also done. Phase 2
is the real remaining work, and its size hinges on baking the inherited group
blend into the inner composite ops at parse time (per §4 (a)) — the
`setAlpha`/image-path change plus ensuring the form's
`GraphicsState` carries the active blend through its parse — and add the test
corpus. The paint side stays untouched (no new paint-time state) if parse-time
baking works; only if it doesn't do we fall back to widening `paintOperand`.
Recommend landing Phase 1 + the test harness first to lock current behaviour,
then the §4 trace, then Phase 2 behind those tests.
