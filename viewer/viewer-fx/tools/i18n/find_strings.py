#!/usr/bin/env python3
"""
Lists string literals in the JavaFX viewer's code that look like text a user reads and don't go
through Messages yet:  python3 viewer/viewer-fx/tools/i18n/find_strings.py [file.java ...]
Heuristic: ids, style classes, property keys, logging and exception internals are skipped; what
is left needs a look.
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.abspath(os.path.join(HERE, '../../src/main/java'))
LIT = re.compile(r'"((?:[^"\\]|\\.)*)"')
SKIP = re.compile(r'logger\.|Logger\.|getStyleClass|setId\(|setStyle\(|lookup|getProperty\(|setProperty\(|'
                  r'Messages\.|@SuppressWarnings|keyCombination|super\(ID|Pattern\.compile|replace(All|First)|'
                  r'\.equals\("|startsWith\("|endsWith\("|new Name\(|case "|throw new \w+Exception\("[a-z]|'
                  r'printf|System\.(out|err)|getResource|"-fx-|Property\(this, "|Wrapper(<>)?\(this, "|'
                  r'public static final String [A-Z_]+ = "|String\.format\("%|\(this, "|return "on[A-Z]|Boolean\.getBoolean|\.store\(|'
                  r'-> "[a-z]+\.[\w.]+";')
files = sys.argv[1:] or [os.path.join(d, f) for d, _, fs in os.walk(SRC) for f in fs if f.endswith('.java')]
count = 0
for path in sorted(files):
    for n, line in enumerate(open(path, encoding='utf-8'), 1):
        s = line.strip()
        if s.startswith(('*', '//', '/*')) or SKIP.search(line):
            continue
        hits = [m.group(1) for m in LIT.finditer(line)
                if re.search(r'[A-Za-z]{2}', m.group(1)) and not re.fullmatch(r'[a-z0-9_.\-/*]+', m.group(1))
                and re.search(r'[A-Z][a-z]|[a-z] [a-z]', m.group(1))]
        if hits:
            count += 1
            print(f'{os.path.relpath(path, SRC)}:{n}: {s[:150]}')
print(count, 'lines to look at')
