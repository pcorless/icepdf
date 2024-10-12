package com.icepdf.fx.scene.control.skin;

import com.icepdf.fx.scene.control.DocumentView;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.graphics.images.ImageUtility;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;

/**
 * Created by pcorl_000 on 2017-03-31.
 */
public class PageCaptureTask extends Task<Image> {

    private ImageView pageImageView;
    private DocumentView documentView;

    private javafx.scene.shape.Rectangle clip;

    private int index;


    public PageCaptureTask(int index, ImageView pageImageView, javafx.scene.shape.Rectangle clip, DocumentView documentView) {
        this.pageImageView = pageImageView;
        this.index = index;
        this.documentView = documentView;
        this.clip = clip;
    }

    @Override
    protected Image call() throws Exception {

        try {
            Catalog catalog = documentView.getDocument().getCatalog();

            Page page = catalog.getPageTree().getPage(index);
            page.init();
            //todo make pageBounder a property
            PDimension sz = page.getSize(Page.BOUNDARY_MEDIABOX, 0, (float) documentView.getScale());

            int pageWidth = (int) sz.getWidth();
            int pageHeight = (int) sz.getHeight();
            if (clip != null && clip.getWidth() > 0) {
                pageWidth = (int) Math.round(clip.getWidth());
                pageHeight = (int) Math.round(clip.getHeight());
            }

            if (isCancelled()) {
                return null;
            }

            BufferedImage image = ImageUtility.createCompatibleImage(pageWidth, pageHeight);
//            System.out.println(pageWidth + " " + pageHeight);
            Graphics g = image.createGraphics();
            g.translate((int) -clip.getX(), (int) -clip.getY());
            page.paint(g, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, (float) documentView.getScale());
            g.dispose();

            if (isCancelled()) {
                return null;
            }

            final Image pageImage = SwingFXUtils.toFXImage(image, null);

            if (!isCancelled()) {
                DocumentViewSkin documentViewSkin = (DocumentViewSkin) documentView.getSkin();
                documentViewSkin.getImageCaptureCache().put(index, new SoftReference<>(pageImage));
                Platform.runLater(() -> {
                    // update the location
                    pageImageView.relocate(clip.getX(), clip.getY());
//                    pageImageView.setScaleX(1);
//                    pageImageView.setScaleY(1);
//                    pageImageView.setTranslateX(0);
//                    pageImageView.setTranslateY(0);
                    pageImageView.setImage(pageImage);
                    pageImageView.setFitWidth(clip.getWidth());
                    pageImageView.setClip(new Rectangle(0, 0, clip.getWidth(), clip.getHeight()));
                    // make sure it's visible.
                    pageImageView.setVisible(true);
                });
            }

            return pageImage;
        } catch (InterruptedException e) {
//            e.printStackTrace();
        }

        return null;
    }
}
