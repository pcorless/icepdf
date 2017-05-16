/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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

import com.google.zxing.*;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ResultParser;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;
import org.icepdf.core.exceptions.PDFException;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.ri.util.FontPropertiesManager;
import org.icepdf.ri.util.PropertiesManager;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

/**
 * The <code>BarcodeReader</code> class is an example how to find and decode barcodes contained in a PDF file. This
 * example use the https://github.com/zxing/zxing library to decode the barcode values and needs to be downloaded.
 * <br>
 * A file specified at the command line is opened and every page in the PDF is scanned for barcodes.  Any barcode
 * data that is found is is written to the console.
 *
 * @since 6.2.4
 */
public class BarcodeReader {

    // barcode reader hints
    private static Map<DecodeHintType, Object> hints;
    static {
        // formats to scan for,; shorter list will make a shorter scan time.
        List<BarcodeFormat> formats = new ArrayList<BarcodeFormat>();
        formats.addAll(Arrays.asList(
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.RSS_14,
                BarcodeFormat.RSS_EXPANDED,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF_417,
                BarcodeFormat.CODABAR,
                BarcodeFormat.MAXICODE));
        hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        // set a few system properties to make sure images are as crisp as possible.
        System.setProperty("org.icepdf.core.print.interpolation", "VALUE_INTERPOLATION_NEAREST_NEIGHBOR");
    }

    public static void main(String[] args) {

        // Get a file from the command line to open
        String filePath = args[0];

        // read/store the font cache.
        ResourceBundle messageBundle = ResourceBundle.getBundle(
                PropertiesManager.DEFAULT_MESSAGE_BUNDLE);
        PropertiesManager properties = new PropertiesManager(System.getProperties(),
                ResourceBundle.getBundle(PropertiesManager.DEFAULT_MESSAGE_BUNDLE));
        new FontPropertiesManager(properties, System.getProperties(), messageBundle);

        // start the barcode scan
        try {
            BarcodeReader barcodeReader = new BarcodeReader();
            barcodeReader.findBarcodes(filePath);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void findBarcodes(String filePath) throws IOException, PDFException, PDFSecurityException,
            InterruptedException, NotFoundException {

        // open the document.
        Document document = new Document();
        document.setFile(filePath);

        // scans found count.
        int found = 0;
        int pages = document.getNumberOfPages();
        for (int i = 0; i < pages; i++) {
            // capture each page, at a slightly higher zoom to give the scanner more data to work with.
            BufferedImage image = (BufferedImage) document.getPageImage(0, GraphicsRenderingHints.PRINT,
                    Page.BOUNDARY_CROPBOX, 0, 3f);

            // ready the barcode scanner area.
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null,
                    0, image.getWidth());
            RGBLuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            // setup a the reader.
            System.out.println("Scanning Page: " + i);
            MultiFormatReader multiFormatReader = new MultiFormatReader();
            MultipleBarcodeReader reader = new GenericMultipleBarcodeReader(multiFormatReader);
            Result[] results;
            try {
                results = reader.decodeMultiple(bitmap, hints);
            } catch (NotFoundException e) {
                System.out.println("\tNo Result.");
                continue;
            }
            for (Result result : results) {
                ParsedResult parsedResult = ResultParser.parseResult(result);
                System.out.println("\tformat: " + result.getBarcodeFormat() +
                        ", type: " + parsedResult.getType() +
                        ", Raw result: " + result.getText());
                found++;
            }
            image.flush();
        }
        System.out.println("Scan complete found " + found +" codes.");
        document.dispose();
    }
}
