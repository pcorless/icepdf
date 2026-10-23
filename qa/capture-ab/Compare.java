/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.TreeSet;

/**
 * Compares two {@link CaptureRun} output folders page by page.
 * <p>
 * {@code java Compare <dir A> <dir B> [threshold=2]}: for each PNG in either folder, prints nothing
 * when the pages are identical, {@code DIFF name pixels>t=N (P%) max=M} when they differ (N pixels
 * differ by more than the threshold in some channel, M the largest channel difference), {@code SIZE}
 * when the sizes differ and {@code MISSING-A}/{@code MISSING-B} when only one side has the page.  A
 * {@code max} at or under the threshold is usually run-to-run noise (anti-aliasing, a race-free but
 * order-dependent blend); check it by capturing the baseline twice.  Ends with a summary line.
 */
public class Compare {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: Compare <dir A> <dir B> [threshold=2]");
            System.exit(2);
        }
        File a = new File(args[0]);
        File b = new File(args[1]);
        int threshold = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        TreeSet<String> names = new TreeSet<>();
        for (File dir : new File[]{a, b}) {
            String[] list = dir.list((d, n) -> n.endsWith(".png"));
            if (list != null) java.util.Collections.addAll(names, list);
        }
        int same = 0, diff = 0, missing = 0;
        for (String name : names) {
            File fa = new File(a, name), fb = new File(b, name);
            if (!fa.exists() || !fb.exists()) {
                System.out.println((fa.exists() ? "MISSING-B " : "MISSING-A ") + name);
                missing++;
                continue;
            }
            BufferedImage ia = ImageIO.read(fa), ib = ImageIO.read(fb);
            if (ia.getWidth() != ib.getWidth() || ia.getHeight() != ib.getHeight()) {
                System.out.printf("SIZE %s %dx%d vs %dx%d%n", name, ia.getWidth(), ia.getHeight(), ib.getWidth(), ib.getHeight());
                diff++;
                continue;
            }
            long over = 0;
            int max = 0;
            for (int y = 0; y < ia.getHeight(); y++) {
                for (int x = 0; x < ia.getWidth(); x++) {
                    int p = ia.getRGB(x, y), q = ib.getRGB(x, y);
                    if (p == q) continue;
                    int d = 0;
                    for (int shift = 0; shift < 32; shift += 8) {
                        d = Math.max(d, Math.abs(((p >> shift) & 255) - ((q >> shift) & 255)));
                    }
                    max = Math.max(max, d);
                    if (d > threshold) over++;
                }
            }
            if (max == 0) {
                same++;
            } else {
                diff++;
                System.out.printf("DIFF %s pixels>%d=%d (%.3f%%) max=%d%n", name, threshold, over,
                        100.0 * over / (ia.getWidth() * (long) ia.getHeight()), max);
            }
        }
        System.out.printf("pages %d identical %d differ %d missing %d%n", names.size(), same, diff, missing);
    }
}
