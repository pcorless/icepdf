# Headless capture A/B

Renders the first pages of a set of documents with two ICEpdf builds and compares them pixel by pixel. It's the same capture the QA app (`qa/viewer-jfx`) does, without the UI, so it can run from a terminal or a script. On a 16-core desktop, the 2,195-document full-monty set takes about 90 seconds per build.

## Running it

Each build is a folder of jars: core, viewer and their dependencies. The QA app's capture-set `libs` folders already have this shape (for example `~/dev/products/icepdf-7.5.0-MAIN/libs`).

```
qa/capture-ab/run-ab.sh <libs A> <libs B> <pdf folder | list file> <out dir> [doc threads=4] [pages=3]
```

- The documents are a folder of PDFs (searched recursively) or a text file with one path per line. Lines starting with `#` are skipped.
- `CAPTURE_HEAP` sets each capture JVM's `-Xmx`; the default is `4g`.
- Output:
  - `A/` and `B/`: one PNG per page (`<file>_<page>.png`).
  - `A/log.txt` and `B/log.txt`: failures and timeouts.
  - `compare.txt`: the comparison.

Each page is captured the way `PageCapture` does it: screen rendering hints, crop box, no rotation, zoom 1. Two threads capture the pages of each document at the same time, as in the QA app, so threading bugs still show as missing content. A document that takes more than 180s is abandoned and logged.

To capture a branch, build its jars and put them next to the dependencies:

```
./gradlew :core:core-awt:jar :viewer:viewer-awt:jar
mkdir -p /tmp/libs-branch
cp ~/dev/products/icepdf-7.5.0-MAIN/libs/*.jar /tmp/libs-branch/
cp core/core-awt/build/libs/icepdf-core-*-SNAPSHOT.jar viewer/viewer-awt/build/libs/icepdf-viewer-*-SNAPSHOT.jar /tmp/libs-branch/
```

The core and viewer jars have the same names in both folders, so the copy replaces them. For a baseline at another commit, use `git worktree add <dir> <commit>` and build there.

## Reading compare.txt

| Line | Meaning |
|---|---|
| (nothing) | The page is identical. |
| `DIFF name pixels>2=N (P%) max=M` | N pixels differ by more than the threshold (default 2 levels) in some channel. M is the largest difference. |
| `SIZE name` | The page sizes differ. |
| `MISSING-A name` / `MISSING-B name` | Only one build produced the page (it failed or timed out on the other). |

The last line counts pages: identical, different and missing.

A `max` of 1 or 2 with no pixels over the threshold is usually run-to-run noise. Capture the baseline twice and compare it with itself to see what's normal for a document.
