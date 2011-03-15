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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        else if (args.length >= 1 && args[0].equals("-prune")) {
            if (args.length >= 3) {
                prune(args[1], args[2]);
            }
            else {
                System.out.println("Need 2 arguments for -prune option: oldCatalogFile newCatalogFile");
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

        ArrayList<File> allFiles = new ArrayList<File>(1024);
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

    public static void prune(String oldCatalogFile, String newCatalogFile) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch(NoSuchAlgorithmException e) {
            System.out.println("Problem getting SHA-256 digest: " + e);
            return;
        }

        ObjectOutputStream newOutput = null;
        try {
            FileOutputStream fos = new FileOutputStream(newCatalogFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, 4096*4);
            newOutput = new ObjectOutputStream(bos);
        }
        catch(IOException e) {
            System.out.println("Problem openning newCatalogFile: " + e);
        }

        ArrayList<File> allFiles = null;
        try {
            FileInputStream fis = new FileInputStream(oldCatalogFile);
            BufferedInputStream bis = new BufferedInputStream(fis, 4096*4);
            ObjectInputStream ois = new ObjectInputStream(bis);
            allFiles = (ArrayList<File>) ois.readObject();
            ois.close();
        }
        catch(IOException e) {
            System.out.println("Problem openning oldCatalogFile: " + e);
            return;
        }
        catch(ClassNotFoundException e) {
            System.out.println("Problem reading catalog from ["+oldCatalogFile+"]: " + e);
            return;
        }

        System.out.println("Found " + allFiles.size() + " PDF files to process");
        
        ArrayList<File> prunedFiles = new ArrayList<File>(Math.max(allFiles.size(), 1));
        HashMap<FileHash, String> hash2path =
                new HashMap<FileHash, String>(allFiles.size());
        HashMap<String, ArrayList<DuplicateEntry>> duplicatePaths =
                new HashMap<String, ArrayList<DuplicateEntry>>();
        
        byte[] buffer = new byte[8*1024];
        int numMissing = 0;
        int numDuplicates = 0;
        long then = 0;
        for(int i = 0; i < allFiles.size(); i++) {
            File file = allFiles.get(i);
            String path = file.getAbsolutePath();
            long now = System.currentTimeMillis();
            if ((now - then) >= 5000L) {
                then = now;
                System.out.println("Commencing processing file " + (i+1) + " of " + allFiles.size());
                //System.out.println(path);
            }
            if (!file.exists()) {
                System.out.println("Removed non-existant '"+path+"'");
                numMissing++;
                continue;
            }
//System.out.println(path);
            try {
                FileInputStream fis = new FileInputStream(file);
                digest.reset();
                while (true) {
                    int read = fis.read(buffer);
                    if (read < 0)
                        break;
                    digest.update(buffer, 0, read);
                }
                fis.close();
            }
            catch(IOException e) {
                System.out.println("Problem hashing '"+path+"' : " + e);
            }

            FileHash fh = new FileHash(digest.digest(), file.length());
            String originalPath = hash2path.get(fh);
            if (originalPath == null) {
                hash2path.put(fh, path);
                prunedFiles.add(file);
            }
            else {
                ArrayList<DuplicateEntry> duplicates = duplicatePaths.get(originalPath);
                if (duplicates == null) {
                    duplicates = new ArrayList<DuplicateEntry>();
                    duplicatePaths.put(originalPath, duplicates);
                }
                DuplicateEntry de = new DuplicateEntry(originalPath, path, i);
                duplicates.add(de);
                numDuplicates++;
//System.out.println("DUPLICATE: " + path);
//System.out.println("       OF: " + originalPath);
            }
        }
        prunedFiles.trimToSize();
        System.out.println("Finished processing " + allFiles.size() + " PDF files. Found " + numDuplicates + " duplicates, and " + numMissing + " missing.");
        
        /*
        for (int i = 0; i < allFiles.size(); i++) {
            File file = (File) allFiles.get(i);
            String path = file.getAbsolutePath();
            ArrayList<DuplicateEntry> duplicates = duplicatePaths.get(path);
            if (duplicates == null)
                continue;
            System.out.println("ORIGINAL: " + (new Date((new File(path)).lastModified())) + "\t" + path);
            for (int j = 0; j < duplicates.size(); j++) {
                System.out.println("     DUP: " + (new Date((new File(duplicates.get(j).duplicatePath)).lastModified())) + "\t" + duplicates.get(j).duplicatePath);
            }
        }
        */
        
        for(File file : prunedFiles) {
            System.out.println(file.getAbsolutePath());
        }

        try {
            newOutput.writeObject(prunedFiles);
            newOutput.flush();
            newOutput.close();
        }
        catch(IOException e) {
            System.out.println("Problem saving newCatalogFile: " + e);
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
            ois.close();
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
            //long before = System.currentTimeMillis();
            tagPdf(path);
            //long after = System.currentTimeMillis();
            //System.out.println("Duration: " + (after-before));
            System.out.println("Used memory: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        }
        System.out.println("Finished tagging " + allFiles.size() + " PDF files");
        //if (true) return;

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

    private static void recursivelyCatalogPDFs(java.util.ArrayList<File> allFiles, File directory) {
        File[] children = directory.listFiles();
        if(children != null && children.length > 0) {
            java.util.ArrayList<File> directories = new java.util.ArrayList<File>(children.length);
            java.util.ArrayList<File> files = new java.util.ArrayList<File>(children.length);
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

    private static void addFileIfIsPDF(java.util.List<File> list, File file) {
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


    private static class FileHash {
        private byte[] digestBytes;
        private long fileLength;
        private int hash;
        
        FileHash(byte[] digestBytes, long fileLength) {
            this.digestBytes = digestBytes;
            this.fileLength = fileLength;
            
            hash = (int) (fileLength ^ (fileLength >> 32));
            for (int i = digestBytes.length - 1; i >= 0; i--) {
                hash = 31 * hash + digestBytes[i];
            }
        }
        
        public int hashCode() {
            return hash;
        }
        
        public boolean equals(Object other) {
            if (other instanceof FileHash) {
                FileHash fh = (FileHash) other;
                if (hash != fh.hash || fileLength != fh.fileLength)
                    return false;
                if (digestBytes.length != fh.digestBytes.length)
                    return false;
                for (int i = digestBytes.length - 1; i >= 0; i--) {
                    if (digestBytes[i] != fh.digestBytes[i])
                        return false;
                }
                return true;
            }
            return false;
        }
    }
    
    private static class DuplicateEntry {
        String originalPath;
        String duplicatePath;
        int duplicateIndex;
        
        DuplicateEntry(String originalPath, String duplicatePath, int duplicateIndex) {
            this.originalPath = originalPath;
            this.duplicatePath = duplicatePath;
            this.duplicateIndex = duplicateIndex;
        }
    }
}
