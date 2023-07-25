package org.icepdf.ri.common.filters;


import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.util.GraphicsRenderingHints.SCREEN;

/**
 * Implementation of a filter filtering empty pages from a document
 */
public class EmptyPagesFilter extends AbstractImageDocumentFilter {

    private static final Logger logger = Logger.getLogger(EmptyPagesFilter.class.getName());

    private final double minColoredPixelsFraction;
    private final int maxRedValue;
    private final int maxGreenValue;
    private final int maxBlueValue;


    private final Map<Document, AtomicBoolean> cancellations;

    private final Map<Document, CompletableFuture<?>> futures;

    /**
     * Instantiates the filter
     *
     * @param minColoredPixelsFraction The minimum fraction of pixels passing the color test to consider that a page is not empty
     * @param maxColorValue            The maximum red, green or blue value that a pixel can have to be considered non-white (white = 255)
     */
    public EmptyPagesFilter(final double minColoredPixelsFraction, final int maxColorValue) {
        this(minColoredPixelsFraction, maxColorValue, maxColorValue, maxColorValue);
    }

    /**
     * Instantiates the filter
     *
     * @param minColoredPixelsFraction The minimum fraction of pixels passing the color test to consider that a page is not empty
     * @param maxRedValue              The maximum red value that a pixel can have to be considered non-white (white = 255)
     * @param maxGreenValue            The maximum green value that a pixel can have to be considered non-white (white = 255)
     * @param maxBlueValue             The maximum blue value that a pixel can have to be considered non-white (white = 255)
     */
    public EmptyPagesFilter(final double minColoredPixelsFraction, final int maxRedValue, final int maxGreenValue, final int maxBlueValue) {
        super(prepareParameters(minColoredPixelsFraction, maxRedValue, maxGreenValue, maxBlueValue));
        this.minColoredPixelsFraction = minColoredPixelsFraction;
        this.maxRedValue = maxRedValue;
        this.maxGreenValue = maxGreenValue;
        this.maxBlueValue = maxBlueValue;
        this.futures = new ConcurrentHashMap<>();
        this.cancellations = new ConcurrentHashMap<>();
    }

    private static Map<String, Object> prepareParameters(final double minColoredPixelsFraction, final int maxRedValue,
                                                         final int maxGreenValue, final int maxBlueValue) {
        final Map<String, Object> parameters = new HashMap<>(5);
        parameters.put("minColoredPixelsFraction", minColoredPixelsFraction);
        parameters.put("maxRedValue", maxRedValue);
        parameters.put("maxGreenValue", maxGreenValue);
        parameters.put("maxBlueValue", maxBlueValue);
        return parameters;
    }

    @Override
    protected Set<Integer> filterPagesUncached(final Document document) {
        if (document != null) {
            cancellations.put(document, new AtomicBoolean(false));
            final int numberOfPages = document.getNumberOfPages();
            final Set<Integer> filtered = new HashSet<>(numberOfPages);
            final PageTree pt = document.getPageTree();
            if (pt != null) {
                final Set<CompletableFuture<Boolean>> allFutures = new HashSet<>();
                for (int i = 0; i < pt.getNumberOfPages(); ++i) {
                    final int index = i;
                    final CompletableFuture<Boolean> future = supply(() -> filterPage(document, index));
                    future.whenComplete((ret, t) -> {
                        if (ret) {
                            filtered.add(index);
                        }
                    });
                    allFutures.add(future);
                }
                futures.put(document, CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])));
                try {
                    futures.get(document).get();
                    logger.info("Filtering done for " + document.getDocumentOrigin() + " (" + numberOfPages + " pages)");
                } catch (final ExecutionException e) {
                    logger.log(Level.SEVERE, "Filtering failed for " + document.getDocumentOrigin(), e);
                } catch (final InterruptedException | CancellationException e) {
                    logger.log(Level.INFO, "Filtering cancelled for " + document.getDocumentOrigin());
                } finally {
                    futures.remove(document);
                    cancellations.remove(document);
                }
            }
            return filtered;
        } else {
            return Collections.emptySet();
        }
    }

    private boolean filterPage(final Document document, final int index) {
        if (!cancellations.computeIfAbsent(document, d -> new AtomicBoolean(true)).get()) {
            try {
                final BufferedImage image = (BufferedImage) document.getPageImage(index, SCREEN, Page.BOUNDARY_CROPBOX, 0, 0.5f);
                final int total = image.getWidth() * image.getHeight();
                int pixelCount = 0;
                for (int x = 0; x < image.getWidth(); ++x) {
                    for (int y = 0; y < image.getHeight(); ++y) {
                        final Color color = new Color(image.getRGB(x, y), false);
                        if (color.getRed() < maxRedValue || color.getGreen() < maxGreenValue || color.getBlue() < maxBlueValue) {
                            pixelCount++;
                        }
                    }
                }
                final double ratio = (double) pixelCount / total;
                return ratio > minColoredPixelsFraction;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        return "EmptyPagesFilter";
    }

    @Override
    public void interrupt(final Document document) {
        final CompletableFuture<?> future = futures.getOrDefault(document, null);
        if (future != null && !future.isDone() && !future.isCancelled()) {
            future.cancel(true);
        }
        final AtomicBoolean cancelled = cancellations.computeIfAbsent(document, d -> new AtomicBoolean(true));
        cancelled.set(true);
    }
}
