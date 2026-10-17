<!-- Moved from JPX-GH613-PLAN.md (2026-10-17) so it travels with the tests that cite it. -->
# JPEG 2000 reader: ImageIO contract (GH-613)

The decoder ships as a normal `javax.imageio` plug-in. It isn't an ICEpdf-only API, so any Java
code (`ImageIO.read(file)`, TwelveMonkeys users, other PDF libraries) gets JPEG 2000 just by adding
the jar. ICEpdf talks to it only through ImageIO, exactly as it talks to JJ2000 today. Swapping
the decoder means swapping a jar.

**Registration**
- `META-INF/services/javax.imageio.spi.ImageReaderSpi`, plus `provides` in `module-info` so it
  also works on the module path.
- Found by `ImageIO.getImageReaders(stream)`, `getImageReadersByFormatName("jpeg2000")`,
  `...BySuffix("jp2")` and `...ByMIMEType("image/jp2")`. Format names `jpeg2000`, `jpeg 2000`,
  `jp2`, `j2k`, `jpx`; suffixes `jp2 j2k j2c jpx jpf`; MIME types `image/jp2`, `image/jpx`.
- `canDecodeInput` sniffs without consuming: `mark()`/`reset()` on the `ImageInputStream`, and
  only the SOC/SIZ or JP2 signature is checked.
- When JJ2000 is also on the class path, implement `onRegistration` to order ours ahead of it
  with `IIORegistry.setOrdering`. ICEpdf additionally prefers ours by SPI class name.
- Fat jars must merge the services files: unpacking jars keeps only the first
  `META-INF/services/javax.imageio.spi.ImageReaderSpi` (the viewers' Gradle `mergeServices` task and
  Maven `metaInf-services` containerDescriptorHandler exist for this).

**`ImageReader` surface**
- `setInput` takes `ImageInputStream` only, not `byte[]`. `getNumImages` returns 1.
- `getWidth`/`getHeight` read only the headers.
- `getImageTypes` / `getRawImageType` describe the real bands and precision.
- `isImageTiled` / `getTileWidth` / `getTileHeight` report the codestream tiling, with `readTile`
  supported.
- `read(0, param)` honours `ImageReadParam`:
  - source region
  - source subsampling (mapped onto discarded resolution levels first)
  - source and destination bands
  - destination type and offset
- `canReadRaster` is true. `readRaster` returns raw component values with no colour conversion.
  This is the path ICEpdf uses.
- `read` applies `colr` (enumerated or ICC), `pclr` and `cdef`, so plain `ImageIO.read` users
  get correct colours.
- Listeners and lifecycle: `IIOReadProgressListener` / `IIOReadWarningListener` (recoverable
  stream damage becomes a warning, not an exception). `abort()` is checked between tiles and
  code-blocks. `reset()` and `dispose()` release buffers.
- Metadata: `getImageMetadata` covers the standard `javax_imageio_1.0` tree (chroma,
  dimension/resolution from `res`) plus a minimal native tree (boxes, SIZ/COD summary).
  `getStreamMetadata` returns null.

**Tests:** `Jpeg2000ImageReaderContractTest` with the same shape as TwelveMonkeys'
`ImageReaderAbstractTest` (or that class itself, if the project goes there). Run it against JJ2000
in Phase 0 to record which behaviours the old reader gets wrong (`baseline/contract-*.tsv`), then
against ours. Still owed: a plain `ImageIO.read(File)` smoke test with no ICEpdf on the class path.
