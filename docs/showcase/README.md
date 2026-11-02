# Showcase document

`expedition-report.pdf` is the sample document used for the site's screenshots and videos: a fictional
expedition field report built to show what icepdf renders and edits.
- Cover: a photo under a translucent gradient sky, with a see-through title panel.
- Summary page: two-column text, a table and a chart, plus highlights and sticky notes from four reviewers.
- Field log: made-up personal details, there to be redacted.
- Sign-off page: form fields and an empty signature field.

All people, places, phone numbers (555-01xx) and permit numbers are made up. The cover photo is by Patrick Corless.

To rebuild it, edit `report.html` (layout) or `build.py` (form fields and annotations), then run `python3 build.py`.
This needs `pypdf` and `google-chrome`. Chrome prints the HTML, and pypdf adds what HTML can't express:
- AcroForm fields and the signature field
- highlights and notes with saved appearances
