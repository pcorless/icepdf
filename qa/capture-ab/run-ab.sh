#!/usr/bin/env bash
# Copyright 2026 Patrick Corless
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Headless A/B of two ICEpdf builds: captures the first pages of each document with each build,
# then compares them.  See README.md.
#
#   qa/capture-ab/run-ab.sh <libs A> <libs B> <pdf folder | list file> <out dir> [doc threads] [pages]
set -euo pipefail
if [ $# -lt 4 ]; then
    echo "usage: $0 <libs A> <libs B> <pdf folder | list file> <out dir> [doc threads=4] [pages=3]" >&2
    exit 2
fi
LIBS_A=$(cd "$1" && pwd)
LIBS_B=$(cd "$2" && pwd)
DOCS=$3
OUT=$4
THREADS=${5:-4}
PAGES=${6:-3}
HEAP=${CAPTURE_HEAP:-4g}
HERE=$(cd "$(dirname "$0")" && pwd)

mkdir -p "$OUT/classes"
# compiled against build A; both classes only use PageCapture, GraphicsRenderingHints and Page.
javac -d "$OUT/classes" -cp "$LIBS_A/*" "$HERE/CaptureRun.java" "$HERE/Compare.java"

for side in A B; do
    if [ "$side" = A ]; then libs=$LIBS_A; else libs=$LIBS_B; fi
    echo "== capture $side ($libs)"
    java -Xmx"$HEAP" -Djava.awt.headless=true -cp "$OUT/classes:$libs/*" CaptureRun "$DOCS" "$OUT/$side" "$THREADS" "$PAGES" \
        > "$OUT/capture-$side.log" 2>&1
    tail -1 "$OUT/capture-$side.log"
done

echo "== compare"
java -Djava.awt.headless=true -cp "$OUT/classes" Compare "$OUT/A" "$OUT/B" > "$OUT/compare.txt"
tail -1 "$OUT/compare.txt"
if ! diff -q "$OUT/A/log.txt" "$OUT/B/log.txt" > /dev/null; then
    echo "failures differ between A and B:"
    diff "$OUT/A/log.txt" "$OUT/B/log.txt" || true
fi
echo "details: $OUT/compare.txt"
