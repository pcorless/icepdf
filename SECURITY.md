# Security Policy

## Supported versions

Security fixes go into the latest release line.

| Version | Supported          |
|---------|--------------------|
| 8.0.x   | :white_check_mark: |
| < 8.0   | :x:                |

## Reporting a vulnerability

Please report vulnerabilities privately, not in a public issue or discussion:

1. Go to the repository's [Security tab](https://github.com/pcorless/icepdf/security).
2. Choose **Report a vulnerability** and describe the problem, the affected versions and how to reproduce it.
   A PDF that triggers it helps most; it stays private to the report.

You'll get a reply in the report. Once a fix is released, the advisory is published with credit to the
reporter, unless you'd rather not be named.

## Scope

icepdf parses PDF documents that often come from untrusted sources, so the most relevant reports are documents
that make the library:

- run code, read or write files, or open network connections it shouldn't;
- leak content that redaction was meant to remove, or that encryption was meant to protect;
- accept a modified or forged digital signature as valid;
- hang or exhaust memory out of proportion to the document's size.

Problems in a third-party library (Bouncy Castle, Apache FontBox, jbig2-imageio, jpx-imageio) are welcome too,
since a fix may need a dependency update or a workaround in icepdf.
