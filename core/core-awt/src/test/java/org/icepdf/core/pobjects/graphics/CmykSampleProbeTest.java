/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GH-501 strategic probe (not a unit test -- opt-in via {@code -Dcmyk.probe=1}).
 * Renders page 0 of each corpus PDF with CMYK-sample preservation ON and reports
 * the K-channel (black ink) statistics of the preserved DeviceCMYK samples.
 * <p>
 * Answers: does the blending corpus carry genuine black ink?  Subtractive blending
 * equals additive RGB for K=0 content (see {@link CmykBlendingSpace}), so the
 * raster-level subtractive path only has a payoff target where {@code maxK > 0}.
 * Point it at directories with {@code -Dcmyk.probe.dirs=/a:/b}.
 */
@EnabledIfSystemProperty(named = "cmyk.probe", matches = "1")
public class CmykSampleProbeTest {

    /** Run headless via Gradle:  ./gradlew :core:core-awt:cmykProbe [-Pcmyk.probe.dirs=/a:/b] */
    public static void main(String[] args) {
        new CmykSampleProbeTest().probeCmykBlackInk();
    }

    @Test
    public void probeCmykBlackInk() {
        String dirs = System.getProperty("cmyk.probe.dirs",
                "/home/pcorless/dev/pdf-qa/graphics/blending"
                        + ":/home/pcorless/dev/pdf-qa/graphics/blending/Multiply"
                        + ":/home/pcorless/dev/pdf-qa/graphics/blending/overlay"
                        + ":/home/pcorless/dev/pdf-qa/graphics/blending/screen");
        List<File> pdfs = new ArrayList<>();
        for (String d : dirs.split(":")) {
            File dir = new File(d);
            File[] files = dir.listFiles((f, n) -> n.toLowerCase().endsWith(".pdf"));
            if (files != null) {
                Arrays.sort(files);
                pdfs.addAll(Arrays.asList(files));
            }
        }
        System.out.println("\n==== GH-501 CMYK black-ink probe : " + pdfs.size() + " files ====");
        System.out.printf("%-52s %7s %12s %12s %5s%n",
                "file", "images", "pixels", "K>8 px", "maxK");
        List<String> withBlack = new ArrayList<>();
        for (File pdf : pdfs) {
            boolean prev = ImageUtility.setPreserveCmyk(true);
            ImageUtility.clearCmykSamples();
            Document doc = null;
            try {
                doc = new Document();
                doc.setFile(pdf.getAbsolutePath());
                if (doc.getNumberOfPages() > 0) {
                    Page page = doc.getPageTree().getPage(0);
                    page.init();
                    org.icepdf.core.pobjects.PDimension sz =
                            page.getSize(Page.BOUNDARY_CROPBOX, 0f, 1f);
                    int w = Math.max(1, Math.min(2000, (int) sz.getWidth()));
                    int h = Math.max(1, Math.min(2000, (int) sz.getHeight()));
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    page.paint(g, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, 0f, 1f);
                    g.dispose();
                    img.flush();
                }
                long[] s = ImageUtility.cmykKSummary();
                String name = pdf.getName();
                if (name.length() > 50) name = name.substring(0, 49) + "~";
                System.out.printf("%-52s %7d %12d %12d %5d%n", name, s[0], s[1], s[2], s[3]);
                if (s[3] > 0) {
                    withBlack.add(String.format("%s  (maxK=%d, K>8 px=%d)", pdf.getName(), s[3], s[2]));
                }
            } catch (Exception e) {
                System.out.printf("%-52s  ERROR %s%n", pdf.getName(), e);
            } finally {
                if (doc != null) doc.dispose();
                ImageUtility.clearCmykSamples();
                ImageUtility.setPreserveCmyk(prev);
            }
        }
        System.out.println("\n---- files with genuine black ink (raster-path payoff targets) ----");
        if (withBlack.isEmpty()) {
            System.out.println("  NONE: every preserved CMYK image is K=0 -> subtractive == additive for this corpus.");
        } else {
            withBlack.forEach(s -> System.out.println("  " + s));
        }
        System.out.println("==== end probe ====\n");
    }
}
