/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.tag;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.tag.query.Expression;
import org.icepdf.core.tag.query.Querior;
import org.icepdf.core.tag.query.DocumentResult;
import org.icepdf.core.tag.query.ParseException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.ObjectOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author mcollette
 * @since 4.0
 */
public class TagUtil {
    public static void main(String args[]) {
        if(!Tagger.tagging) {
            System.out.println("PDF tagging not enabled");
            return;
        }

        if (args.length >= 1 && args[0].equals("-catalog")) {
            if (args.length >= 3) {
                catalog(args[1], args[2]);
            }
            else {
                System.out.println("Need 2 arguments for -catalog option: contentRoot and catalogFile");
            }
        }
        else if (args.length >= 1 && args[0].equals("-tag")) {
            if (args.length >= 3) {
                tag(args[1], args[2]);
            }
            else {
                System.out.println("Need 2 arguments for -tag option: catalogFile tagFile");
            }
        }
        else if (args.length >= 1 && args[0].equals("-query")) {
            if (args.length >= 3) {
                String tagFile = args[1];
                String[] rpnQuery = new String[args.length-2];
                System.arraycopy(args, 2, rpnQuery, 0, args.length-2);
                query(tagFile, rpnQuery);
            }
            else {
                System.out.println("Need at least 2 arguments for -query option: tagFile queryArguments...");
                System.out.println("queryArguments is specified in RPN format. Eg: 'and or A B or C D' means (A or B) and (C or D)");
            }
        }
    }

    public static void catalog(String contentRoot, String catalogFile) {
        //TODO add support for refreshing or adding to catalog

        // Try to open the output file before spending all that time doing
        // processing, in case the file path was wrong.
        ObjectOutputStream oos = null;
        try {
            FileOutputStream fos = new FileOutputStream(catalogFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 4096*4);
            oos = new ObjectOutputStream(bos);
        }
        catch(IOException e) {
            System.out.println("Problem openning catalogFile: " + e);
            return;
        }

        ArrayList allFiles = new ArrayList(1024);
//System.out.println("contentRoot: " + contentRoot);
        System.out.println("Cataloging list of PDFs...");
        File contentRootFile = new File(contentRoot);
        if (contentRootFile.isDirectory()) {
//System.out.println("Directory: " + contentRootFile);
            recursivelyCatalogPDFs(allFiles, contentRootFile);
        }
        else if (contentRootFile.isFile()) {
//System.out.println("File: " + contentRootFile);
            addFileIfIsPDF(allFiles, contentRootFile);
        }
        allFiles.trimToSize();
        System.out.println("Cataloged " + allFiles.size() + " PDF files");

        try {
            oos.writeObject(allFiles);
            oos.flush();
            oos.close();
        }
        catch(IOException e) {
            System.out.println("Problem saving catalog of PDF files: " + e);
        }
    }

    public static void tag(String catalogFile, String tagFile) {
        ObjectOutputStream tagOutput = null;
        try {
            FileOutputStream fos = new FileOutputStream(tagFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 4096*4);
            tagOutput = new ObjectOutputStream(bos);
        }
        catch(IOException e) {
            System.out.println("Problem openning tagFile: " + e);
        }

        ArrayList allFiles = null;
        try {
            FileInputStream fis = new FileInputStream(catalogFile);
            BufferedInputStream bis = new BufferedInputStream(fis, 4096*4);
            ObjectInputStream ois = new ObjectInputStream(bis);
            allFiles = (ArrayList) ois.readObject();
        }
        catch(IOException e) {
            System.out.println("Problem openning catalogFile: " + e);
            return;
        }
        catch(ClassNotFoundException e) {
            System.out.println("Problem reading catalog from ["+catalogFile+"]: " + e);
            return;
        }

        System.out.println("Found " + allFiles.size() + " PDF files to tag");
        long then = 0;
        for(int i = 0; i < allFiles.size(); i++) {
            File file = (File) allFiles.get(i);
            String path = file.getAbsolutePath();
            long now = System.currentTimeMillis();
            if ((now - then) >= 5000L) {
                then = now;
                System.out.println("Commencing tagging file " + (i+1) + " of " + allFiles.size());
                System.out.println(path);
            }
            tagPdf(path);
//System.out.println("Used memory: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        }
        System.out.println("Finished tagging " + allFiles.size() + " PDF files");

        try {
            TagState state = Tagger.getTagState();
            tagOutput.writeObject(state);
            tagOutput.flush();
            tagOutput.close();
        }
        catch(IOException e) {
            System.out.println("Problem saving tags: " + e);
        }
    }

    public static void query(String tagFile, String[] rpnQuery) {
        Expression expression = null;
        try {
            expression = Querior.parse(rpnQuery);
        }
        catch(ParseException e) {
            System.out.println("Problem parsing expression: " + e);
            return;
        }
        if (expression == null)
            return;
        TagState state = null;
        try {
            FileInputStream fis = new FileInputStream(tagFile);
            BufferedInputStream bis = new BufferedInputStream(fis, 4096*4);
            ObjectInputStream tagInput = new ObjectInputStream(bis);
            state = (TagState) tagInput.readObject();
            tagInput.close();
        }
        catch(IOException e) {
            System.out.println("Problem reading from tagFile: " + e);
            e.printStackTrace();
            return;
        }
        catch(ClassNotFoundException e) {
            System.out.println("Problem reading TagState from tagFile: " + e);
            return;
        }
        List documentResults = Querior.search(state, expression);
        if (documentResults == null || documentResults.size() == 0) {
            System.out.println("No results");
            return;
        }
        for (int i = 0; i < documentResults.size(); i++) {
            DocumentResult docRes = (DocumentResult) documentResults.get(i);
            System.out.println(docRes.getDocument().getOrigin());
            List images = docRes.getImages();
            System.out.println("  Matching images: " + images.size());
            for (int j = 0; j < images.size(); j++) {
                TaggedImage ti = (TaggedImage) images.get(j);
                System.out.println("  Image: " + (ti.getReference() != null ? ti.getReference().toString() : "<unreferenced>") + "  Inline: " + ti.isInlineImage() + "  Pages: " + ti.describePages());
            }
        }
    }

    private static void recursivelyCatalogPDFs(java.util.ArrayList allFiles, File directory) {
        File[] children = directory.listFiles();
        if(children != null && children.length > 0) {
            java.util.ArrayList directories = new java.util.ArrayList(children.length);
            java.util.ArrayList files = new java.util.ArrayList(children.length);
            for(int i = 0; i < children.length; i++) {
                if(children[i].isDirectory()) {
                    String name = children[i].getName();
                    if (!name.startsWith(".")) {
                        directories.add(children[i]);
                    }
                }
                else if(children[i].isFile()) {
                    addFileIfIsPDF(files, children[i]);
                }
            }
            Collections.sort(directories);
            Collections.sort(files);
            for(int i = 0; i < directories.size(); i++) {
//System.out.println("Directory: " + directories.get(i));
                recursivelyCatalogPDFs(allFiles, (File) directories.get(i));
            }
            allFiles.addAll(files);
        }
    }

    private static void addFileIfIsPDF(java.util.List list, File file) {
        if (file.getName().toLowerCase().endsWith(".pdf"))
            list.add(file);
    }

    private static void tagPdf(String path) {
        Document pdfDoc = new Document();
        try {
            pdfDoc.setFile(path);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(Tagger.tagging)
            Tagger.setCurrentDocument(pdfDoc);

        int numberOfPages = pdfDoc.getNumberOfPages();
        for (int pageNumber = 0; pageNumber < numberOfPages; pageNumber++) {
            if(Tagger.tagging)
                Tagger.setCurrentPageIndex(pageNumber);

            Page page = pdfDoc.getPageTree().getPage(pageNumber, TagUtil.class);
            if (page != null) {
                page.init();
                pdfDoc.getPageTree().releasePage(page, TagUtil.class);
                // So the system remains responsive
                try { Thread.sleep(20L); } catch(InterruptedException e) {}
            }
        }
        pdfDoc.dispose();
    }
}
