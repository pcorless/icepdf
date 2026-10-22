#!/usr/bin/env python3
"""
Fills the JavaFX viewer's translations from the Swing viewer's, where the English text matches.

    python3 viewer/viewer-fx/tools/i18n/sync_translations.py [--report]

For every key of messages.properties whose English matches a Swing MessageBundle text (same
words, case preferred; a trailing ':' '...' or '…' ignored; the same {n} placeholders), each
locale gets the Swing translation (the most common one when several Swing keys match).

A locale file has two sections: the first is rewritten from Swing on every run; the second,
after the line "# ---- translated for the JavaFX viewer", is kept as it is - hand translations
and drafts - except for keys English no longer has.  A key in the second section wins over
Swing's match, so a hand translation is never replaced.  de_CH holds only differences from de.
"""
import collections, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '../../../..'))
SWING = os.path.join(REPO, 'viewer/viewer-awt/src/main/resources/org/icepdf/ri/resources/MessageBundle')
FX = os.path.join(REPO, 'viewer/viewer-fx/src/main/resources/org/icepdf/fx/i18n/messages')
LOCALES = ['da', 'de', 'de_CH', 'es', 'fi', 'fr', 'it', 'nl', 'no', 'pt', 'sv', 'zh_CN', 'zh_TW']
NAMES = {'da': 'Danish', 'de': 'German', 'de_CH': 'Swiss German (differences from German)', 'es': 'Spanish',
         'fi': 'Finnish', 'fr': 'French', 'it': 'Italian', 'nl': 'Dutch', 'no': 'Norwegian', 'pt': 'Portuguese',
         'sv': 'Swedish', 'zh_CN': 'Chinese (Simplified)', 'zh_TW': 'Chinese (Traditional)'}
OWN = '# ---- translated for the JavaFX viewer'
HEADER = '''#
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
# {name}.  Keys missing here fall back to messages.properties (English).
#
# ---- from the Swing viewer's translation (rewritten by tools/i18n/sync_translations.py)
'''


def parse(text):
    """key -> value, in order, for .properties text (escapes and continuations resolved)."""
    text = re.sub(r'\\\r?\n[ \t]*', '', text)
    out = {}
    for line in text.splitlines():
        s = line.lstrip()
        if not s or s[0] in '#!':
            continue
        m = re.match(r'((?:[^=:\s\\]|\\.)+)\s*[=:]?\s*(.*)$', s)
        if not m:
            continue
        v = re.sub(r'\\u([0-9a-fA-F]{4})', lambda g: chr(int(g.group(1), 16)), m.group(2))
        v = re.sub(r'\\(.)', lambda g: {'n': '\n', 't': '\t', 'r': '\r'}.get(g.group(1), g.group(1)), v)
        out[m.group(1)] = v
    return out


def load(path):
    return parse(open(path, encoding='utf-8').read()) if os.path.exists(path) else {}


def escape(v):
    v = v.replace('\\', '\\\\').replace('\n', '\\n').replace('\t', '\\t')
    return '\\' + v if v.startswith(' ') else v


SUFFIX = re.compile(r'(\s*(?:\.\.\.|…|:|：|\.|。))$')


def split(v):
    v = v.strip()
    m = SUFFIX.search(v)
    return (v[:m.start()], m.group(1).strip()) if m else (v, '')


def norm(v):
    return re.sub(r'\s+', ' ', split(v.replace('&', ''))[0]).lower()


def placeholders(v):
    return sorted(re.findall(r'\{\d[^}]*\}', v))


def from_swing(fx):
    en = load(SWING + '.properties')
    tr = {loc: load(f'{SWING}_{loc}.properties') for loc in LOCALES}
    by_norm = collections.defaultdict(list)
    for k, v in en.items():
        by_norm[norm(v)].append(k)
    result = {loc: {} for loc in LOCALES}
    for key, val in fx.items():
        cands = [c for c in by_norm.get(norm(val), []) if placeholders(en[c]) == placeholders(val)]
        exact = [c for c in cands if split(en[c].replace('&', ''))[0] == split(val)[0]]
        cands = exact or cands
        if not cands:
            continue
        suffix = split(val)[1]
        for loc in LOCALES:
            votes = collections.Counter(split(tr[loc][c].replace('&', ''))[0] + suffix for c in cands if c in tr[loc])
            if not votes:
                continue
            best = votes.most_common(1)[0][0]
            if val[:1].isupper() and best[:1].islower():
                best = best[:1].upper() + best[1:]
            # with arguments a value is a MessageFormat pattern: an apostrophe is written ''
            best = re.sub(r"(?<!')'(?!')", "''", best) if re.search(r'\{\d', val) else best.replace("''", "'")
            result[loc][key] = best
    return result


def own_section(path):
    """The kept section of an existing locale file, as raw lines, and its keys."""
    if not os.path.exists(path):
        return [], {}
    text = open(path, encoding='utf-8').read()
    if OWN not in text:
        return [], {}
    tail = text[text.index(OWN):].split('\n', 1)[1] if '\n' in text[text.index(OWN):] else ''
    return tail.rstrip('\n').split('\n') if tail.strip() else [], parse(tail)


def main():
    fx = load(FX + '.properties')
    swing = from_swing(fx)
    for loc in LOCALES:
        path = f'{FX}_{loc}.properties'
        entries = swing[loc]
        if loc == 'de_CH':
            entries = {k: v for k, v in entries.items() if swing['de'].get(k) != v}
        lines, own = own_section(path)
        # a hand translation wins over Swing's match
        entries = {k: v for k, v in entries.items() if k not in own}
        # a kept line for a key English no longer has is dropped
        kept = [l for l in lines if not (re.match(r'([^#!=\s][^=]*)=', l) and l.split('=', 1)[0] not in fx)]
        with open(path, 'w', encoding='utf-8') as f:
            f.write(HEADER.format(name=NAMES[loc]))
            for k in fx:
                if k in entries:
                    f.write(f'{k}={escape(entries[k])}\n')
            f.write('\n' + OWN + ' (hand translations; "draft" sections need review)\n')
            if kept:
                f.write('\n'.join(kept) + '\n')
        own_keys = sum(1 for l in kept if re.match(r'[^#!=\s][^=]*=', l))
        print(f'{loc:6} from Swing {len(entries):4}, own {own_keys:4}, of {len(fx)}')
    if '--report' in sys.argv:
        print(json.dumps({loc: sorted(set(fx) - set(swing[loc])) for loc in ['fr']}, indent=1)[:2000])


if __name__ == '__main__':
    main()
