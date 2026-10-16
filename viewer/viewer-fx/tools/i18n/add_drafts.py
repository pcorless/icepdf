#!/usr/bin/env python3
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
"""
Adds draft translations to the locale files, marked for review:

    python3 viewer/viewer-fx/tools/i18n/add_drafts.py <dir with <locale>.tsv files> [label]

Each <locale>.tsv line is "key<TAB>text" or "key=text" (\\n for a newline; write apostrophes plainly - they are
doubled for MessageFormat patterns here, twice over inside a {n,choice,...} plural).  Keys already translated (from Swing, or by hand) are
left alone.  The drafts go at the end of the file's "translated for the JavaFX viewer" section,
under a "# ---- draft (<label>): needs review" heading; once a line is reviewed, move it above
that heading (or just delete the heading when the whole block is).  de_CH gets the de drafts with
"ß" written "ss", where that differs.
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from sync_translations import FX, LOCALES, OWN, escape, load  # noqa: E402

Q2, Q4 = "'" * 2, "'" * 4

src = sys.argv[1]
label = sys.argv[2] if len(sys.argv) > 2 else 'machine translation'
base = load(FX + '.properties')


def read_tsv(path):
    out = {}
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if not line.strip() or line.startswith('#'):
            continue
        key, text = line.split('\t', 1) if '\t' in line else line.split('=', 1)
        if key not in base:
            sys.exit(f'{path}: {key} is not in messages.properties')
        text = text.replace('\\n', '\n')
        if re.search(r'\{\d', base[key]):
            text = re.sub(r"(?<!')'(?!')", "''", text)
            # inside {n,choice,...} the choice format reads quotes again: a doubled quote there is written four times
            text = re.sub(r'\{\d,choice,[^{}]*(?:\{\d\}[^{}]*)*\}', lambda m: m.group(0).replace(Q2, Q4), text)
            if sorted(set(re.findall(r'\{(\d)', base[key]))) != sorted(set(re.findall(r'\{(\d)', text))):
                sys.exit(f'{path}: {key} arguments differ from English: {text}')
        out[key] = text
    return out


drafts = {}
for loc in LOCALES:
    path = os.path.join(src, loc + '.tsv')
    if os.path.exists(path):
        drafts[loc] = read_tsv(path)
if 'de' in drafts and 'de_CH' not in drafts:
    drafts['de_CH'] = {k: v.replace('ß', 'ss') for k, v in drafts['de'].items() if 'ß' in v}

for loc, entries in drafts.items():
    path = f'{FX}_{loc}.properties'
    have = load(path)
    if loc == 'de_CH':
        have = {**load(f'{FX}_de.properties'), **have}
        entries = {k: v for k, v in entries.items() if have.get(k) != v}
    new = {k: v for k, v in entries.items() if k not in load(path) and (loc != 'de_CH' or True)}
    if loc != 'de_CH':
        new = {k: v for k, v in entries.items() if k not in have}
    if not new:
        print(f'{loc:6} nothing new')
        continue
    text = open(path, encoding='utf-8').read().rstrip('\n')
    if OWN not in text:
        text += '\n\n' + OWN + ' (hand translations; "draft" sections need review)'
    block = [f'\n# ---- draft ({label}): needs review'] + [f'{k}={escape(new[k])}' for k in base if k in new]
    open(path, 'w', encoding='utf-8').write(text + '\n' + '\n'.join(block) + '\n')
    print(f'{loc:6} {len(new)} drafts')
