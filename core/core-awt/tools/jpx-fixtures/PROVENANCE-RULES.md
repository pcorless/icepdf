<!-- Moved from JPX-GH613-PLAN.md (2026-10-17) so it travels with the tests that cite it. -->
# JPEG 2000 decoder: provenance and audit trail (GH-613)

Requirement from day one, not cleaned up afterwards. Anyone adopting the code (TwelveMonkeys, a
legal review, us in five years) must be able to answer for each piece of code: did it come from
pdf.js, from OpenJPEG, from the spec, or did we write it, and on what evidence.

## Origin categories

| Tag | Meaning | Must record |
|---|---|---|
| `PORTED` | Close translation of upstream code: same structure and logic, Java idioms only | upstream project, file, function(s), **commit hash**, licence; original copyright block kept |
| `ADAPTED` | Upstream logic restructured, e.g. per-tile instead of whole-image, or merged from two sources | as for `PORTED`, plus a one-line note on what changed and why |
| `SPEC` | Written from ITU-T T.800 / ISO 15444-1 text without copying upstream code | spec edition + clause/annex (e.g. "T.800 (06/2019) Annex D.3.4"); any equations or tables taken from it |
| `ORIGINAL` | Our own design: ImageIO plumbing, tests, harness, the per-tile raster path | nothing extra |
| `INFERRED` | Behaviour not stated by spec or code but decided from **observation**: oracle output, corpus files, broken-encoder workarounds (TNsot), tolerance choices | the evidence: fixture or corpus file, oracle and version, measured result, date |

`INFERRED` matters most to get right. These are the judgement calls, e.g. "OpenJPEG 2.5.4 clamps
here, so we clamp" or "encoder X writes TNsot one short". Each one should be traceable to a
reproducible observation, not to memory.

## Where it's recorded (three layers, kept in agreement)

1. **Per file.** The header states the origin tag(s). `PORTED`/`ADAPTED` files keep the upstream
   copyright block under our Apache header, e.g.:
   ```
   * Provenance: ADAPTED from OpenJPEG libopenjp2/t1.c @ <hash> (BSD-2), opj_t1_dec_clnpass*;
   *             PORTED from pdf.js src/core/jpx.js @ 674e7ee38 (Apache-2.0), decodeCodeblock
   ```
   Mixed files tag the individual methods.
2. **`PROVENANCE.md`** in the decoder repo (until that exists, this directory's `DECISIONS.md`
   holds the `INFERRED` entries). A table with one row per file or method group:
   origin tag, upstream ref + hash, licence, notes. `INFERRED` rows link to an entry in
   `DECISIONS.md` (evidence, oracle version, date). NOTICE lists every upstream whose code is in
   the shipped jar.
3. **Commits.** Every commit that brings in upstream logic says so in the message body
   (`Ported-from: openjpeg t1.c @ <hash>`). One upstream source per commit where practical, so
   `git log` alone can reconstruct the lineage.

## Rules

- **Pin upstream.** Clone OpenJPEG at a tag (start at v2.5.4, the version pdf.js ships) and pdf.js
  at `674e7ee38`, and record both hashes in `PROVENANCE.md`. Never port from an unpinned
  `master`.
- **Clean-room for JJ2000.** Its code is never opened while writing decoder code. JJ2000 is used
  only as a black-box oracle (its output), and that use is recorded as `INFERRED` evidence. Grok
  and Kakadu source: never.
- **No unattributed snippets.** Nothing pasted from StackOverflow, blogs or other decoders.
- **AI-assisted code** is held to the same rule. When a model writes a method, it gets the origin
  tag of what it was working from, and the commit carries the `Co-Authored-By` trailer as usual.
  "The model produced it" is never a valid origin.
- **Tests carry provenance too.** Fixtures record their generator command and source image.
  Reference decodes record the oracle and its version. Conformance streams (ISO/IEC 15444-4,
  `openjpeg-data`) are never committed: their terms aren't clear.
- **Check it mechanically.** A small check (a Gradle task or a unit test) fails the build when a
  `src/main` file has no `Provenance:` line, or when a hash or file named in a header is missing
  from `PROVENANCE.md`.
