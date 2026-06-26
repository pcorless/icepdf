# Design Note: Buffer-Free Transparency-Group Blending

**Status:** draft / proposal — Phase 1 (de-fuzz classifier) implemented; Phase 2 not started; oversized-SMask routing+NPE fixed (§ bug 1/2); shading-luminosity-mask render fixed (§9 bug 3: sentinel overflow + Type 3 stitching function); backdrop-aware non-isolated compositing IN PROGRESS (§10 — the white-fill root fix; P0 backdrop-replay done & validated, P1 fixes photo-backdrop cases, P2 spec-correct draw-back DONE (corpus-clean, 0 regressions); single-render + full separable blends DONE; whole reference triad improves, 0 corpus regressions; wider validation + perf TODO before default-on; flag `org.icepdf.core.backdropComposite`, default off).
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
- **Phase 2 — DEFERRED to a separate ticket/branch.** Extend the inline path to
  separable non-Normal blends (Multiply et al.) via a render-scoped blend-aware
  composite (the `TRANSPARENT_BACKDROP` ThreadLocal pattern applied to
  `AlphaDrawCmd`, set by start/end markers around the group's shapes). This is
  where `1.pdf`-style groups stop needing a buffer at all and the remaining
  per-image downsample (§5.1.1 stage 1) goes away — each image draws straight to
  the page raster at the viewing zoom. Gated by the §4 non-overlap constraint.

  Not started here. It is a change to the **hottest paint path** (`AlphaDrawCmd`
  runs for every fill/stroke/image/text alpha) and needs broad corpus validation,
  so it is its own effort under a separate ticket/branch.

  **Known hard part (from prior attempts):** going buffer-free does not remove
  the size problem, it moves it. Painting the group inline means each image draws
  at `native × page-zoom` device pixels, so at high zoom the *inline* draw can
  blow up memory exactly as a buffer would. The inline path therefore still needs
  a **zoom-aware size cap** — bound the device footprint actually rasterised
  (clamp to on-screen size, tile, or fall back to a buffer past a threshold), not
  just the user-space bbox. Designing that cap is the crux of Phase 2, not the
  blend-aware composite.
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

### 5.2.5 The white-fill is a *white-backdrop proxy* — proven by two files

Two real documents pin down exactly what the white-fill is and why no local
heuristic can fix it (investigated 2026-06-25):

- `P100002202` (Earth Day): a black rectangle under a luminosity soft mask inside
  a non-isolated Multiply `ca 0.4` group, sitting over a **photo**. It should fade
  black→**transparent** (revealing the solar-panel photo); ICEpdf renders it
  black→**white**. Disabling the white-fill fixes it (matches mutool).
- `transparency_start`: non-isolated Multiply groups over a **white page**. It
  renders **correctly only because** of the white-fill — removing it regresses
  hard (mutool 4.9→25.6 *and* poppler 6.5→27.3, so not a mutool artifact).

So the white-fill is a **stand-in for the group's real backdrop, hard-coded to
white.** It is right exactly when the page behind the group *is* white
(`transparency_start`) and wrong when it is anything else (`P100002202`'s photo).
No local signal separates the two: both are non-isolated Multiply groups with
soft masks and overlapping `ca` ranges (Earth Day .2–.4; transparency_start
.35–1.0). Attempts that all failed corpus validation: remove white-fill
(3 better / 3 worse / 4 neutral), broaden `TRANSPARENT_BACKDROP` to all groups
(same), and post-paint "fill only fully-transparent holes white" (degenerates to
no-white-fill because the content is partial-alpha throughout).

**The real fix is "transparency all the way through":** a non-isolated group must
composite against its **actual page backdrop**, not a white proxy — i.e. seed the
group buffer with the page pixels under its bbox (or paint the group inline onto
the page), then apply the PDF non-isolated **backdrop-removal** formula when
compositing the result back. That is the proper model §5.2.4 calls for; these two
files are its concrete justification. It is architectural work, not a heuristic.

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

### 5.1.1 Where 1.pdf's remaining softness comes from — three scaling stages

A per-image trace (decoded size, draw transform, buffer size) of 1.pdf's group
found the detail loss is **not** a single scale but three compounding stages:

1. **Per-image downsample (dominant, unaddressed).** Each of the ~49 scans
   decodes at *full* resolution (`1431×2300`, `1398×2062`, …) and is drawn into
   a `~280×450` device region in the group buffer — a **~5× downscale per
   image**. Inherent to buffering: the buffer is ~7742 px wide for 49 scans
   (~158 px each) while the scans are ~1400 px each; holding their native detail
   would need a ~68,000-px-wide buffer. No affine shear is involved, just scale
   plus the normal Y-flip. **Only Phase 2 (inline, no buffer — images draw
   straight to the page at the viewing zoom) recovers this.**
2. **Redundant second rasterisation.** `applyExplicitOutline` re-renders the
   *entire* group a second time (full size) to build an alpha-trim outline. For
   an opaque line-art group the outline adds nothing but doubles the draw work
   ("paint time is large"). Skipping it outright is **not** safe — it trims
   white-fill gaps for non-Multiply blends and changed 12/27 corpus docs
   (`InterponSpecManual` visibly regressed), so it stays. Cost only.
3. **Reconciling resample (fixed, commit `37a9ca98b`).** The main buffer is
   area-scaled (`7742×517`) but the outline was built at the per-dimension clamp
   (`7742×631`), so `applyExplicitOutline` called `scaleImagesToSameSize` and
   **resampled the already-down-scaled main buffer** — extra softening. Now the
   outline is sized to the main buffer (`7742×517`) so the resample is a no-op;
   1.pdf only (26/27 identical), line-art markedly sharper.

Net: stages 1 (area budget, height 134→517) and 3 (size-match) are fixed and
1.pdf is much improved; **stage 1's per-image 5× downsample is the remaining
loss and needs Phase 2.** Stage 2 is pure cost (halved by Phase 2, or by a future
safe-scoped outline skip).

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

## 9. Shading-based luminosity soft masks render empty (bug 3) — RESOLVED

**Resolved** in `833a99b94` (Function_3) + `ebf884b0a` (shading overflow). It was
*two* coupled bugs:

1. **Sentinel-bbox rasteriser overflow (`FormDrawCmd`).** The `sh` fill shape
   defaulted to the mask form's `±Short.MAX_VALUE` bbox; scaled by the mask's `cm`
   (~2077×) it mapped to ~1e8 device coords and overflowed Java2D → zero coverage
   → empty mask. Fix: when the transformed fill shape exceeds a safe device range,
   substitute the buffer region (reconstructed via the same `translate∘cm` the
   fill runs under) so the gradient lands 1:1; normal-sized shading masks
   untouched.
2. **Type 3 stitching function mis-evaluated (`Function_3`) — the real cause of
   the "shifted" gradient.** `calculate()` returned `functions[b]` for the
   interior interval `[bounds[b], bounds[b+1])` instead of `functions[b+1]`, and
   `encode()` only sub-domain-encoded segment 0. A white / white→black-ramp /
   black stitching gradient collapsed to a near-step, mispositioning the fade.
   Fix: interior interval uses `functions[b+1]`; `encode()` uses the sub-domain
   for all segments. Affects *all* Type 3 functions (gradients, transfer/tint
   functions), so corpus-validated: 8/27 docs change, 4 closer to the mutool
   reference, 4 neutral, 0 regressions; core tests green.

Result: WhiteGradient's gradient fade matches the mutool reference (distance-to-ref
37.9 → 30.5; the overflow fix *alone* was 45.3, confirming the two are coupled).

---
*Original investigation notes (kept for context):*

**Symptom.** `WhiteGradient.pdf` (and `P100001613...`, same PDF producer) paint a
white overlay (`Fm0`: `0 0 0 0 k` fill) faded by a `/Luminosity` soft mask whose
mask form (`/G`) paints a *shading* (`BX /Sh0 sh EX`, an axial `ShadingType 2`
DeviceGray gradient). The gradient fade never appears. Routing+NPE fixes (commit
`f797ebacb`) get the group into the buffered SMask path, but the **mask
sub-buffer comes out fully transparent**, so luminosity = 0 → the white overlay
is entirely masked away. mutool renders it correctly (photo faded under white);
ICEpdf shows the photo at full strength.

**What is and isn't the cause (already ruled out by buffer/`Shapes` dumps):**
- The base buffer (white fill) is correct: solid opaque white.
- The mask form's draw commands are all present and well-formed: `TransformDrawCmd`
  (the `cm` `0 -2077.2 -2077.2 0 -43.2 2682`), a **non-null** `LinearGradientPaint`
  (`org.icepdf.core...batik...LinearGradientPaint`), a `ShapeDrawCmd` whose shape
  is set to the form bbox bounds, and a `FillDrawCmd`, under opaque alpha.
- So it is **not** a null paint, missing fill, or alpha=0. The fill simply
  produces no pixels in the buffer.

**Where the bug lives.** `FormDrawCmd.createBufferXObject`'s shading special-case
(the `else` branch ~line 373: set null shapes to `bBox.getBounds2D()`,
`canvas.translate(-x, -y)`, `canvas.setClip(bBox.getBounds2D())`) plus the
shading paint's coordinate space. Three coordinate hazards interact:
1. The mask form's BBox is a **±32768 sentinel** `[-32768 32767 32767 -32768]`,
   so the fill shape and clip are a 65535-unit box, not real geometry.
2. `-x,-y` is the **main form's** (`Fm0`) origin, not the mask form's — deliberate
   (the mask must align to the main buffer) but it has to agree with where the
   `LinearGradientPaint` axis lands.
3. The `cm` is applied via the `TransformDrawCmd` in the shapes, *and* the
   `LinearGradientPaint` carries its own `matrix` — risk of the gradient
   transform being applied twice (or in the wrong space), so the axis maps far
   outside the 1866×2079 buffer. (With `NO_CYCLE` that should clamp to an end
   colour, yet the result is transparent — so confirm whether the paint context
   is going degenerate/empty rather than off-region.)

**Plan**
1. **Pin the exact mechanism** (do first, it decides the fix). Instrument the
   fill: log the `LinearGradientPaint` start/end and `matrix`, the effective
   device transform at `FillDrawCmd` (`translate ∘ cm`), and where the axis maps
   relative to the buffer; sample the whole mask buffer (min/max), not just the
   centre, to confirm it is truly empty. Decide between: (a) gradient transform
   double-applied / wrong space, (b) paint context degenerate (e.g. zero-length
   axis after transform), (c) fill region clipped out by the sentinel-derived
   clip.
2. **Fix the rasterisation.** Likely: stop driving the shading fill off the
   sentinel bbox — clip/fill to the **main buffer bounds** (the region the mask
   actually applies to), and reconstruct the shading paint's transform so the
   `cm` is applied exactly once, in the buffer's coordinate space. Prefer folding
   the shading mask into the *same* path the non-shading luminosity mask uses
   rather than the current fragile special-case.
3. **Validate.** WhiteGradient + P100001613 must match a reference (mutool /
   high-cap) within SSIM; the gradient fade must appear. Full Multiply corpus
   neutral except shading-SMask docs (which improve). Core tests green. Add a
   small synthetic shading-luminosity-mask fixture to `blending/`.

**Risks.** This is the path the code itself flags as unsupported ("not properly
aligning the form or mask space to correctly apply a shading pattern"). Sentinel
bboxes, nested-form coordinate spaces, and the batik gradient paint context make
it fragile; gate every change against the corpus and both producer files.

## 10. Backdrop-aware non-isolated group compositing — PLAN ("transparency all the way through")

The root cause of the recurring "fades to white instead of transparent" bug
(§5.2.5): ICEpdf renders every transparency group into an **isolated** offscreen
buffer and, for additive-CS non-Normal-blend groups, seeds it **white** as a
proxy for the page backdrop. That proxy is correct only when the page behind the
group actually is white. The real fix is to composite a **non-isolated** group
against its **real** backdrop. This section scopes that work.

### 10.1 Reference triad (the acceptance set)

Three real files, same producer family, span the cases. Validate every change
against all three **and** the Multiply corpus, cross-checking with **mutool +
poppler + ghostscript** (mutool alone is unreliable — see §9 / RXV540440).

| File | Backdrop | Today | Want |
|------|----------|-------|------|
| `transparency_start.pdf` | **white page** | correct (white proxy happens to match) | stay correct |
| `P100002202` (Earth Day) | **photo** | black→**white** band | black→transparent (reveal photo) |
| `pattern_and_CYMK_jpeg.pdf` | **CMYK JPEG** | **white patches** | dark JPEG shows through |

`pattern_and_CYMK_jpeg` is the stress case: **many nested `/I false` groups**,
luminosity soft masks **with `/BC` backdrop colour**, over a CMYK JPEG.

### 10.2 The correct model (PDF 32000-1 §11.4.7–11.4.8, §11.3.7.2)

- An **isolated** group (`/I true`) composites against a fully transparent
  backdrop, then the result composites onto the page. ICEpdf's isolated buffer +
  the §5.2.3 `TRANSPARENT_BACKDROP` rule already approximate this.
- A **non-isolated** group (`/I false` — all three files) composites against the
  **group backdrop** (the page content behind it). The spec computes the group in
  the presence of that backdrop, then **removes the backdrop's contribution** so
  the group can be composited back with its own `ca`/blend without double-counting
  it. Backdrop removal (spec §11.4.8): `C = Cn + (Cn − C0)·(α0·(1/αg − 1))`, with
  `C0`,`α0` the backdrop colour/alpha and `Cn`,`αg` the in-group composited
  result/alpha. The white-fill is a degenerate stand-in for `C0 = white`.

### 10.3 Implementation approach (A: backdrop-seeded buffer + removal)

Chosen approach: keep the buffer (needed for soft masks / group `ca`), but seed
it with the **real backdrop** and remove it on the way out.

1. **Plumb the page backdrop to `FormDrawCmd`.** This is the foundational enabler
   and the main missing piece: `paintOperand` has only a `Graphics2D`, not the
   page raster, so it cannot read what is behind the group. Options: (a) expose
   the page's backing `BufferedImage` through the paint context / `Shapes.paint`
   so a group can read the sub-raster under its device bbox; (b) capture the
   backdrop region just-in-time before painting the group. (a) is cleaner and
   reusable.
2. **Seed instead of white-fill.** For a non-isolated group, copy the backdrop
   sub-raster into the group buffer (at the buffer's possibly-down-scaled
   resolution — align with the §5.1 area budget) rather than `fillRect` white.
   Isolated groups keep the transparent + `TRANSPARENT_BACKDROP` path.
3. **Render the group content** over the seeded backdrop (inner blends now
   multiply/etc. against the real backdrop — fixes the §5.2 bug-2 class too,
   retiring the need for the white-fill *and* the `TRANSPARENT_BACKDROP` scoped
   hack).
4. **Remove the backdrop** (§11.4.8 formula) per pixel before the buffer is
   composited back, so the page content under the group is not multiplied twice.
5. **Soft masks with `/BC`:** the mask's backdrop colour seeds the *mask* group's
   backdrop the same way; wire `/BC` (e.g. obj 17/28/48 in `pattern_and_CYMK`)
   into the mask render rather than ignoring it.
6. **Retire the white-fill** and re-scope/`remove` `TRANSPARENT_BACKDROP` once the
   seeded-backdrop path subsumes both.

### 10.4 Hard parts / risks

- **Backdrop readback plumbing** (10.3.1) — the real work; everything else builds
  on it. Must handle the headless `BufferedImage` page target and any tiled paint.
- **Nested non-isolated groups** (`pattern_and_CYMK_jpeg`): the backdrop for a
  nested group is the *parent group's buffer-in-progress*, not the page — the seed
  must come from the enclosing buffer, so this has to work recursively, not just
  at page level.
- **Coordinate / resolution alignment:** backdrop sampled in device space vs the
  group buffer's (possibly area-scaled, translated, `cm`-warped) space.
- **Double-composite correctness:** getting backdrop removal exactly right;
  off-by-a-formula re-introduces darkening/white. Unit-test the removal math
  against hand-computed pixels before trusting renders.
- **Performance:** a raster copy + per-pixel removal per group; bound it (skip for
  groups with no backdrop interaction, reuse the area-budget downscale).

### 10.5 Phasing

- **P0 — Backdrop plumbing.** Expose the page/parent backdrop sub-raster to
  `FormDrawCmd`. No behaviour change yet; add a debug dump to prove the right
  pixels are captured for the triad.
- **P1 — Seed + remove, non-isolated, no mask, flag-gated.** Replace white-fill
  for the simplest non-isolated additive Multiply group; validate Earth Day moves
  toward reference and `transparency_start` stays correct (white backdrop seeds
  white → same result). Corpus-gate.
- **P2 — Soft masks + `/BC`.** Extend to masked groups; `pattern_and_CYMK_jpeg`
  is the target.
- **P3 — Nested groups + retire white-fill / `TRANSPARENT_BACKDROP`.** Make the
  seed recursive; delete the hacks once the triad + corpus pass on all three
  renderers.
- Each phase behind a flag, default off until P3 proves the triad + corpus
  regression-free.

### 10.6 Definition of done

All three triad files match the poppler/ghostscript reference (not just mutool)
within SSIM; Multiply corpus shows no regressions (only the known
white-proxy-dependent docs *improve*); white-fill and the scoped
`TRANSPARENT_BACKDROP` flag are removed; backdrop-removal math has unit tests.

### 10.7 Progress (commit a1099fd90, flag `org.icepdf.core.backdropComposite`, default off)

- **P0 DONE & validated.** Backdrop reconstruction by replaying the stack works
  with no raster readback. `Shapes.paintBackdrop(g, uptoIndex)` replays
  `[0, index)`; `FormDrawCmd.captureBackdrop` renders it into a buffer aligned 1:1
  to the group buffer via `scale ∘ translate(-x,-y) ∘ curTransform⁻¹ ∘ base`,
  seeded with **white paper** (the page background is the render target's initial
  fill, not a draw command, so it must be seeded or a group over blank paper sees
  a transparent backdrop). Verified visually: each group's captured backdrop is
  what's behind it (the two band gradients correctly see the sky strips).
- **P1 PARTIAL.** `compositeOverBackdrop` renders the content over the real
  backdrop *and* over transparent (for the group alpha `αg`), then
  `removeBackdrop` applies §11.4.8. Photo-/image-backdrop docs improve toward the
  poppler/mutool reference: Earth Day 29.6→25.5, Sea Turtle 50.4→46.9,
  pattern_and_CYMK 46.1→39.8. The white-band artifact is gone on Earth Day.
- **Remaining problem (the next nut).** The **white-page Multiply** case
  (`transparency_start`) still regresses (4.9→25.4). Root: the white-fill is
  *itself* the correct backdrop seed for a white page (`Multiply(white-seed,
  white-page)` doesn't double-count), whereas seeding the real backdrop and
  drawing back with Multiply *does* double-count it — which backdrop-removal must
  cancel. But the removal (`C = Cn + (Cn−C0)·(α0·(1/αg−1))`) is derived for a
  spec-correct over-composite, while ICEpdf's `BlendComposite` Multiply draw-back
  is a **simplified** `dst + (B(src,dst)−dst)·alpha` (no proper per-pixel alpha
  compositing). The two are inconsistent, so the removed result re-composited via
  the simplified Multiply doesn't reconstruct the right pixels.
- **Next step.** Make removal and draw-back consistent: either (a) composite the
  group result back with a **spec-correct** Porter-Duff+blend operator (not the
  simplified `BlendComposite`) so §11.4.8 holds, or (b) skip removal and draw the
  *seeded buffer* back with `SRC` over the group's clip while applying the group
  `ca`/blend only to the contribution. Validate the full triad (white page +
  photo + nested) on poppler+ghostscript+mutool before turning the flag on by
  default and retiring the white-fill.

### 10.8 Draw-back composite: SRC_OVER vs group blend (commit b44eed851)

The seeded buffer holds the group composited over its real backdrop; the removed
contribution must be drawn back **without** re-applying the backdrop. Two
draw-back operators were measured (flag-on, vs poppler/mutool):

| doc (backdrop) | white-fill | Multiply draw-back | SRC_OVER draw-back |
|----------------|-----------:|-------------------:|-------------------:|
| transparency_start (white) | 4.9 | 25.4 ✗ | **5.6** ✓ |
| P100002010 (white-ish) | 26.8 | 35.6 ✗ | **25.8** ✓ |
| Earth Day (photo) | 29.6 | 25.5 ✓ | **24.6** ✓ |
| Sea Turtle (photo) | 50.4 | 46.9 ✓ | **46.0** ✓ |
| pattern_and_CYMK (jpeg) | 46.1 | 39.8 ✓ | 46.1 ≈ |
| test | 27.1 | 30.2 ✗ | 37.7 ✗ |

SRC_OVER is net-better (chosen), but neither is universal: `test`'s group
Multiply is genuinely significant at draw-back, so dropping it (SRC_OVER) loses
it, while keeping it (Multiply) double-counts the backdrop elsewhere. **The
unified answer is to apply the group's own blend at draw-back via a spec-correct
Porter-Duff+blend operator consistent with the §11.4.8 removal — not ICEpdf's
simplified `BlendComposite`.** That is the last piece before the flag can default
on and the white-fill can be retired. Phasing now: P0 ✓ (replay backdrop),
P1 ✓ (seed+remove, photo cases), **P2 = spec-correct draw-back composite (TODO)**,
P3 = nested groups + retire white-fill.

### 10.9 P2 DONE — spec-correct draw-back, corpus-clean (commit de3cc1327)

The draw-back is now the PDF 32000-1 §11.4.6 result-colour formula for an opaque
page backdrop, computed per pixel against the reconstructed backdrop `Cb`:

    Cr = (1 - ca*ag)*Cb + ca*ag*B(Cb, Cs)

`composeContribution` emits the contribution as colour `B(Cb,Cs)` at alpha
`ca*ag` (with `Cs` = isolated group colour from §11.4.8 removal), drawn back with
SRC_OVER over the page (= `Cb`). This applies the group's **own** blend and
constant alpha to its **true** backdrop with no double-count, so it is correct
for white-page *and* photo backdrops, Multiply-significant or not — replacing the
SRC_OVER-vs-Multiply guesswork of §10.8.

**Result (Multiply corpus, flag on, vs poppler/mutool): 13/27 change, 4 better
(Earth Day white band fixed and matches mutool, Sea Turtle 50.4→41.3,
transparency_start 4.9→4.1 beating the white-fill, L010), 9 neutral, 0
regressions.** Flag still default off; flag-off byte-identical to HEAD; core
tests green.

**Remaining before defaulting on + retiring the white-fill:**
- **P3 nested groups.** `pattern_and_CYMK_jpeg` is only neutral because a nested
  group's true backdrop is the *parent buffer in progress*, not the page; the
  replay currently rebuilds from page paper + the parent's prior commands, which
  is an approximation. Make the backdrop capture recursive (seed from the
  enclosing group's buffer).
- **Non-Multiply blends.** `composeContribution` currently implements `B` for
  Multiply and Normal; add the other separable modes for general correctness.
- **Wider validation.** Run the whole `pdf-qa/graphics` tree (poppler+gs+mutool),
  as for the Function_3 fix, before flipping the default.
- **Perf.** Two content renders + a stack replay per group; profile and bound
  (skip when the group has no backdrop interaction; reuse the area-budget scale).

### 10.10 P3 nested-backdrop attempt — reverted (parent-buffer readback)

First P3 cut: a ThreadLocal stack of the buffer-in-progress; a nested group reads
its backdrop from the parent buffer (top of stack) via
`scale ∘ translate(-x,-y) ∘ curTransform⁻¹` instead of the white-paper replay.
Result: it did **not** improve `pattern_and_CYMK_jpeg` (still 46.0) and slightly
nudged `transparency_start` (4.1→5.2), so net-negative — **reverted**, P2 kept.

Lessons for the next attempt:
- `pattern_and_CYMK`'s nested groups likely don't take the `isWhiteFillCandidate`
  compositeOverBackdrop path at all (different blend/CS), so reading the parent
  buffer never engaged — confirm by logging which groups hit the path.
- The parent buffer is painted **twice** (overTransparent + overBackdrop) per the
  two-render removal; a nested read picks up whichever pass is running, so the
  nested backdrop is only valid during the parent's overBackdrop pass. The
  two-render-per-group design interacts badly with nesting (exponential, and
  ambiguous backdrop). A single-render removal (track αg without a second content
  render) would make nesting tractable — worth solving before P3.

### 10.11 Single-render removal DONE (commit 4347f7856)

The two-render scheme is gone. A single render over a transparent backdrop with
`TRANSPARENT_BACKDROP` set yields the isolated group result `(Cs, ag)` directly
(separable blend over transparent → source), so the over-backdrop render and the
§11.4.8 removal arithmetic are unnecessary. Output is equivalent to the
two-render P2 (distance-to-poppler/mutool unchanged to 0.1 across all
backdrop-composite docs; only sub-perceptual rounding diffs), at ~half the cost.

**This unblocks P3 nesting:** with one pass, the parent buffer-in-progress is
well-defined (no over-transparent/over-backdrop ambiguity). Sketch for the next
P3 attempt: push the parent's captured backdrop `Bp` **and** the live parent
buffer `bi` (= parent content-so-far over transparent); a nested group builds its
backdrop as `bi over Bp` mapped into its space (both are in parent-buffer space,
same readback transform). Caveat to confirm first: it's unverified that
`pattern_and_CYMK_jpeg`'s white patches even come from the non-isolated-additive
*non-Normal-blend* path this work targets — they may be **Normal**-blend SMask
groups (which the white-fill never touched), i.e. a different bug. Confirm the
test case before building P3.

### 10.12 pattern_and_CYMK confirmed = blend modes, not nesting (commit 4b1baa713)

Per-group path logging settled the §10.10 question: `pattern_and_CYMK_jpeg`'s
white patches are **Screen/Overlay** groups already on the backdrop-composite
path (`whiteFillCand=true`), not nested groups. The neutrality was purely because
`composeContribution` only handled Multiply; the other separable blends fell back
to Normal. Implementing the full §11.3.5 separable set fixed it: **46.1→42.0**,
and the whole reference triad (Earth Day, transparency_start, pattern_and_CYMK)
now improves, +InterponSpecManual, **0 regressions** on the Multiply corpus.

So **P3 nested-group support is not required for the triad** and is de-prioritised
(the small `Normal`+SMask groups in `pattern_and_CYMK` are a separate, untouched
path; revisit only if a doc surfaces that needs it). Remaining before default-on:
wider `pdf-qa/graphics` validation (poppler+gs+mutool) and perf profiling.

### 10.13 Wide validation toward default-on (whole pdf-qa/graphics tree)

Rendered the entire `pdf-qa/graphics` tree (408 docs) flag-off vs flag-on at HEAD:
**371 identical, 37 changed.** Direction vs poppler (cross-checked mutool+gs):
**22 better, 13 neutral, 2 marginally worse** — `P100001613` (+0.8) and
`CutOff_Head` (+0.6), both **sub-1.0 mean-luma delta, localized to a single
glow/gradient region**, and consistent across all three renderers (so real, not
renderer noise, but tiny).

**Blockers to removing the flag (making backdrop the default + deleting white-fill):**
1. **The 2 marginal regressions.** Localized to one gradient/glow group each.
   Likely gradient-edge/partial-alpha precision in the backdrop reconstruction vs
   the old white-fill path (SoftLight formula was checked and is spec-correct).
   Either resolve, or accept as sub-perceptual given 22 improvements.
2. **Perf.** The backdrop path adds a stack replay + a contribution composite per
   candidate group (now one content render after §10.11). Profile a
   transparency-heavy page; bound it (skip when no candidate groups; cache the
   replay; reuse the area-budget scale).
3. **Flip + retire.** Default `backdropComposite` on, then delete the white-fill
   and the now-subsumed scoped `TRANSPARENT_BACKDROP` plumbing.

This is the closest the white-fill replacement has been to default-ready: a whole
real-world graphics corpus, 22 improvements, 0 large regressions.

### 10.14 The two marginal regressions diagnosed — both resolution/edge, not flaws

- **`P100001613` (+0.8) = under-resolution, not a regression.** Its only candidate
  is a huge Screen group (10538×2734) that the area budget down-scales ~0.37×, so
  the *backdrop reconstruction* loses detail at the glow edges. Re-rendered with a
  larger cap (`maxSmaskImageSize=8000`, less down-scale) flag-on scores **10.84 vs
  flag-off's 14.99 — markedly better.** So the backdrop approach is the more
  correct one here; the default buffer is simply too small for the reconstruction.
  Fix = **device-pixel-aware buffer sizing** (§5.1 option 2): size the group buffer
  to its on-screen footprint, not the raw bbox — a 10538-unit group occupying
  ~964 px on screen needs no down-scale. Resolves this *and* is a general quality
  win.
- **`CutOff_Head` (+0.6) = a narrow edge strip** (diff bbox a ~30 px-wide vertical
  band) from the `ca 0.25` Multiply group; small, not oversized. Likely a
  partial-alpha edge-precision diff at a 4-up panel seam. Minor; characterise
  separately.

**Revised path to default-on:** (1) device-pixel buffer sizing (fixes P100001613
and improves quality generally), (2) confirm/accept the CutOff edge strip, (3)
perf, (4) flip default + delete white-fill. The 22 improvements stand; neither
marginal case is a flaw in the backdrop model.
