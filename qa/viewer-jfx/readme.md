# ICEpdf QA

This is an optional capture/viewer utility for testing the render core for regressions. Capture can be taken for a
particular build and compared against another. Capture results are stored in `~/dev/pdf-qa/results/` but can be
configured via the `PreferencesController`.

## Building

The UI wrapper for the capture tool was written in JFX which adds a hurdle to building the whole library.  
As a result if you find yourself in a situation were you need to do rendering core regression checking you'll need to
uncomment `'qa:viewer-jfx',` in the root settings.gradle config file. There currently isn't a maven build for this
application as it only appeals to a small group of users.

Head over to https://openjfx.io/index.html to install JFX for your version of java. 
