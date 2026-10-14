#!/usr/bin/env python3
"""
Checks the JavaFX viewer's messages:  python3 viewer/viewer-fx/tools/i18n/check_keys.py

- every key the code reads (Messages.get / format with a literal key) is in messages.properties;
- a key read with Messages.format has a pattern with arguments and escapes its apostrophes ('');
- per locale, how many keys are translated.
Exit status 1 when a key is missing.
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '../../../..'))
SRC = os.path.join(REPO, 'viewer/viewer-fx/src/main/java')
FX = os.path.join(REPO, 'viewer/viewer-fx/src/main/resources/org/icepdf/fx/i18n/messages')
sys.path.insert(0, HERE)
from sync_translations import load, LOCALES  # noqa: E402

base = load(FX + '.properties')
code = ''.join(open(os.path.join(d, f), encoding='utf-8').read()
               for d, _, fs in os.walk(SRC) for f in fs if f.endswith('.java'))
literal = r'"([A-Za-z][\w.-]*)"'
used = set(re.findall(r'Messages\.(?:get|format)\(' + literal, code))
used |= set(re.findall(r'\?\s*' + literal + r'\s*:', code)) & set(base) | \
        set(re.findall(r':\s*' + literal + r'\s*\)', code)) & set(base)
formatted = set(re.findall(r'Messages\.format\(' + literal, code))
# a key ending in '.' is a prefix the code completes ("view.mode." + mode)
missing = sorted(k for k in used if k not in base and not k.endswith('.'))
for k in missing:
    print('MISSING', k)
for k in sorted(formatted & set(base)):
    if re.search(r"(?<!')'(?!')", base[k]):
        print("APOSTROPHE (write '' in a pattern)", k, '=', base[k])
for loc in LOCALES:
    t = load(f'{FX}_{loc}.properties')
    print(f'{loc:6} {len(t):4} of {len(base)} translated')
sys.exit(1 if missing else 0)
