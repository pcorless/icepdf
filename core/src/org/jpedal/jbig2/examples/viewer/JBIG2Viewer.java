/**
* ===========================================
* Java Pdf Extraction Decoding Access Library
* ===========================================
*
* Project Info:  http://www.jpedal.org
* (C) Copyright 1997-2008, IDRsolutions and Contributors.
* Main Developer: Simon Barnett
*
* 	This file is part of JPedal
*
* Copyright (c) 2008, IDRsolutions
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above copyright
*       notice, this list of conditions and the following disclaimer in the
*       documentation and/or other materials provided with the distribution.
*     * Neither the name of the IDRsolutions nor the
*       names of its contributors may be used to endorse or promote products
*       derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY IDRsolutions ``AS IS'' AND ANY
* EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL IDRsolutions BE LIABLE FOR ANY
* DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
* (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
* LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
* Other JBIG2 image decoding implementations include
* jbig2dec (http://jbig2dec.sourceforge.net/)
* xpdf (http://www.foolabs.com/xpdf/)
* 
* The final draft JBIG2 specification can be found at http://www.jpeg.org/public/fcd14492.pdf
* 
* All three of the above resources were used in the writing of this software, with methodologies,
* processes and inspiration taken from all three.
*
* ---------------
* JBIG2Viewer.java
* ---------------
*/
package org.jpedal.jbig2.examples.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelGrabber;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;

import org.jpedal.jbig2.JBIG2Decoder;
import org.jpedal.jbig2.JBIG2Exception;
import org.jpedal.jbig2.image.JBIG2Bitmap;

public class JBIG2Viewer extends JFrame {

	private JFrame mainFrame = this;

	private JScrollPane jsp;

	private BufferedImage image;
	private JLabel imageLabel = new JLabel();

	private JComboBox scalingBox;
	private String scalingItem = "";
	private double scaling;

	private JComboBox rotationBox;
	private String rotationItem = "";
	private int rotation;

	private NavigationToolbar navToolbar;

	private JBIG2Decoder decoder;

	private int currentPage;

	public static void main(String[] args) {

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			new JBIG2Viewer();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public JBIG2Viewer() {
		setSize(500, 500);
		getContentPane().setLayout(new BorderLayout());
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		getContentPane().setLayout(new BorderLayout());

		setUpToolbar();

		imageLabel.setHorizontalAlignment(JLabel.CENTER);
		imageLabel.setVerticalAlignment(JLabel.TOP);

		jsp = new JScrollPane(imageLabel);

		getContentPane().add(jsp, BorderLayout.CENTER);

		navToolbar = new NavigationToolbar(this);
		navToolbar.setFloatable(false);
		getContentPane().add(navToolbar, BorderLayout.SOUTH);

		setTitle("JPedal JBIG2 Image Decoder");
		
		setVisible(true);
	}

	private void setUpToolbar() {
		// Create a horizontal toolbar
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);
		toolbar.setBorder(BorderFactory.createEtchedBorder());

		JButton openButton = new JButton(new AbstractAction("Open", new ImageIcon(getClass().getResource("/org/jpedal/jbig2/examples/viewer/res/open.png"))) {
			public void actionPerformed(ActionEvent evt) {
				openFile();
			}
		});
		openButton.setText(null);
		openButton.setToolTipText("Open New File");
		toolbar.add(openButton);

		toolbar.add(Box.createRigidArea(new Dimension(7, 0)));

		JButton saveButton = new JButton(new AbstractAction("Save", new ImageIcon(getClass().getResource("/org/jpedal/jbig2/examples/viewer/res/save.png"))) {
			public void actionPerformed(ActionEvent evt) {
				if (image == null) {
					JOptionPane.showMessageDialog(mainFrame, "No image is open");
				} else {
					saveFile();
				}
			}
		});
		saveButton.setText(null);
		saveButton.setToolTipText("Save File As");
		toolbar.add(saveButton);

		toolbar.add(Box.createRigidArea(new Dimension(7, 0)));

		JButton propertiesButton = new JButton(new AbstractAction("Properties", new ImageIcon(getClass().getResource("/org/jpedal/jbig2/examples/viewer/res/properties.png"))) {
			public void actionPerformed(ActionEvent evt) {
				// Perform action
			}
		});
		propertiesButton.setText(null);
		propertiesButton.setToolTipText("File Properties");
//		toolbar.add(propertiesButton);

		toolbar.add(Box.createRigidArea(new Dimension(7, 0)));

		toolbar.add(new JLabel("Zoom:"));
		toolbar.add(Box.createRigidArea(new Dimension(3, 0)));

		ActionListener actionListener = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setScalingAndRotation();
			}
		};

		scalingBox = new JComboBox(new String[] { "Window", "Height", "Width", "25", "50", "75", "100", "125", "150", "200", "250", "500", "750", "1000" });
		scalingBox.setEditable(true);
		scalingBox.setPreferredSize(new Dimension(scalingBox.getPreferredSize().width, toolbar.getHeight()));
		scalingBox.setPrototypeDisplayValue("XXXXXXXX"); // Set a desired
															// width
		scalingBox.setMaximumSize(new Dimension(100, 100));
		toolbar.add(scalingBox);
		toolbar.add(Box.createRigidArea(new Dimension(7, 0)));

		toolbar.add(new JLabel("Rotation:"));
		toolbar.add(Box.createRigidArea(new Dimension(3, 0)));

		rotationBox = new JComboBox(new String[] { "0", "90", "180", "270" });
		rotationBox.setEditable(true);
		rotationBox.setPreferredSize(new Dimension(rotationBox.getPreferredSize().width, toolbar.getHeight()));
		rotationBox.setMaximumSize(new Dimension(100, 100));
		toolbar.add(rotationBox);

		getContentPane().add(toolbar, BorderLayout.NORTH);

		rotationBox.addActionListener(actionListener);
		scalingBox.addActionListener(actionListener);
	}

	private void saveFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.addChoosableFileFilter(new FileFilterer(new String[] { "png" }, "PNG (*.png)"));
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int approved = chooser.showSaveDialog(null);
		if (approved == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			String fileToSave = file.getAbsolutePath();

			if (!fileToSave.toLowerCase().endsWith(".png")) {
				file = new File(fileToSave + ".png");
			}

			try {
				ImageIO.write(image, "png", file);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void openFile() {
		JFileChooser open = new JFileChooser(".");
		open.setFileSelectionMode(JFileChooser.FILES_ONLY);
		String[] png = new String[] { "jb2", "jbig2" };
		open.addChoosableFileFilter(new FileFilterer(png, "JBIG2 (jb2, jbig2)"));

		int resultOfFileSelect = open.showOpenDialog(mainFrame);

		if (resultOfFileSelect == JFileChooser.APPROVE_OPTION) {

			/** reset rotation */
			rotation = 0;
			rotationItem = "0";
			rotationBox.setSelectedItem(rotationItem);

			/** reset scaling */
			scaling = 1;
			scalingItem = "Window";
			scalingBox.setSelectedItem(scalingItem);

			decoder = new JBIG2Decoder();
			try {
				decoder.decodeJBIG2(open.getSelectedFile());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JBIG2Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

//			navToolbar.open();
			navToolbar.setCurrentPage(1);
			navToolbar.setTotalNoOfPages(decoder.getNumberOfPages());

			currentPage = 1;

			image = decoder.getPageAsBufferedImage(currentPage - 1);

			setScalingAndRotation();
		}
	}

	private void setScalingAndRotation() {
		if (image == null)
			return;

		String selectedScaling = (String) scalingBox.getSelectedItem();

		if (selectedScaling.equals("Window")) {
			int windowWidth = jsp.getWidth();
			int windowHeight = jsp.getHeight();

			int imageWidth = image.getWidth();
			int imageHeight = image.getHeight();
			
			if (windowWidth / (double) imageWidth < windowHeight / (double) imageHeight) { // scale by width
				scaleToWidth();
			} else { // scale by height
				scaleToHeight();
			}
			
			jsp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			jsp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
		} else {
			
			jsp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			jsp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
			
			if (selectedScaling.equals("Height")) {
				scaleToHeight();
			} else if (selectedScaling.equals("Width")) {
				scaleToWidth();
			} else { // this might be an int
				try {
					int scale = Integer.parseInt(selectedScaling);
					this.scaling = scale / (double) 100;
				} catch (NumberFormatException e) { // not a number
					e.printStackTrace();
					scalingBox.setSelectedItem(scalingItem);
					return;
				}
			}
		}  

		String selectedRotation = (String) rotationBox.getSelectedItem();

		try {
			this.rotation = Integer.parseInt(selectedRotation);
		} catch (NumberFormatException e) { // not a number
			e.printStackTrace();
			rotationBox.setSelectedItem(rotationItem);
			return;
		}

		Image scaledImage = image.getScaledInstance((int) (image.getWidth() * scaling), -1, Image.SCALE_DEFAULT);

		BufferedImage rotatedImage = rotate(toBufferedImage(scaledImage), rotation * Math.PI / 180);

		imageLabel.setIcon(new ImageIcon(rotatedImage));

		scalingItem = (String) scalingBox.getSelectedItem();
	}

	private void setRotation() {
		String selectedRotation = (String) rotationBox.getSelectedItem();

		try {
			this.rotation = Integer.parseInt(selectedRotation);
		} catch (NumberFormatException e) { // not a number
			e.printStackTrace();
			rotationBox.setSelectedItem(rotationItem);
			return;
		}

		Image rotatedImage = rotate(image, rotation * Math.PI / 180);
		imageLabel.setIcon(new ImageIcon(rotatedImage));

		rotationItem = (String) rotationBox.getSelectedItem();
	}

	private void scaleToWidth() {
		if (image == null)
			return;
		
		scaling = (jsp.getWidth()) / (double) image.getWidth();
	}

	private void scaleToHeight() {
		if (image == null)
			return;
		scaling = (jsp.getHeight()) / (double) image.getHeight();
	}

	private static BufferedImage rotate(BufferedImage src, double angle) {
		if (src == null)
			return null;

		int w = src.getWidth();
		int h = src.getHeight();
		int newW = (int) (Math.round(h * Math.abs(Math.sin(angle)) + w * Math.abs(Math.cos(angle))));
		int newH = (int) (Math.round(h * Math.abs(Math.cos(angle)) + w * Math.abs(Math.sin(angle))));
		AffineTransform at = AffineTransform.getTranslateInstance((newW - w) / 2, (newH - h) / 2);
		at.rotate(angle, w / 2, h / 2);

		BufferedImage dst = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.drawRenderedImage(src, at);
		g.dispose();
		return dst;
	}

	private static boolean hasAlpha(Image image) {
		// If buffered image, the color model is readily available
		if (image instanceof BufferedImage) {
			BufferedImage bimage = (BufferedImage) image;
			return bimage.getColorModel().hasAlpha();
		}

		// Use a pixel grabber to retrieve the image's color model;
		// grabbing a single pixel is usually sufficient
		PixelGrabber pg = new PixelGrabber(image, 0, 0, 1, 1, false);
		try {
			pg.grabPixels();
		} catch (InterruptedException e) {
		}

		// Get the image's color model
		ColorModel cm = pg.getColorModel();
		return cm.hasAlpha();
	}

	private BufferedImage toBufferedImage(Image image) {
		if (image instanceof BufferedImage) {
			return (BufferedImage) image;
		}

		// This code ensures that all the pixels in the image are loaded
		image = new ImageIcon(image).getImage();

		// Determine if the image has transparent pixels; for this method's
		// implementation, see e661 Determining If an Image Has Transparent
		// Pixels
		boolean hasAlpha = hasAlpha(image);

		// Create a buffered image with a format that's compatible with the
		// screen
		BufferedImage bimage = null;
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		try {
			// Determine the type of transparency of the new buffered image
			int transparency = Transparency.OPAQUE;
			if (hasAlpha) {
				transparency = Transparency.BITMASK;
			}

			// Create the buffered image
			GraphicsDevice gs = ge.getDefaultScreenDevice();
			GraphicsConfiguration gc = gs.getDefaultConfiguration();
			bimage = gc.createCompatibleImage(image.getWidth(null), image.getHeight(null), transparency);
		} catch (HeadlessException e) {
			// The system does not have a screen
		}

		if (bimage == null) {
			// Create a buffered image using the default color model
			int type = BufferedImage.TYPE_INT_RGB;
			if (hasAlpha) {
				type = BufferedImage.TYPE_INT_ARGB;
			}
			bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), type);
		}

		// Copy image to buffered image
		Graphics g = bimage.createGraphics();

		// Paint the image onto the buffered image
		g.drawImage(image, 0, 0, null);
		g.dispose();

		return bimage;
	}

	public static void displayJBIG2AsImage(JBIG2Bitmap bitmap) {

		byte[] bytes = bitmap.getData(true);

		if (bytes == null)
			return;

		// make a a DEEP copy so we cant alter
		int len = bytes.length;
		byte[] copy = new byte[len];
		System.arraycopy(bytes, 0, copy, 0, len);

		int width = bitmap.getWidth();
		int height = bitmap.getHeight();

		/** create an image from the raw data */
		DataBuffer db = new DataBufferByte(copy, copy.length);

		WritableRaster raster = Raster.createPackedRaster(db, width, height, 1, null);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
		image.setData(raster);

		Image scaledImage = image.getScaledInstance(500, -1, Image.SCALE_AREA_AVERAGING);

		BufferedImage result = new BufferedImage(scaledImage.getWidth(null), scaledImage.getHeight(null), BufferedImage.TYPE_INT_BGR);
		Graphics2D g = result.createGraphics();
		g.drawImage(scaledImage, 0, 0, null);
		g.dispose();

		JLabel label = new JLabel(new ImageIcon(result));

		JOptionPane.showConfirmDialog(null, label, "JBIG2 Display", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE);
	}

	public JBIG2Decoder getDecoder() {
		return decoder;
	}

	public void displayPage(int page) {
		if (image != null && page > 0 && page <= decoder.getNumberOfPages()) {
			image = decoder.getPageAsBufferedImage(page - 1);
			currentPage = page;
			setScalingAndRotation();
			navToolbar.setCurrentPage(currentPage);
		}
	}

	public int getCurrentPage() {
		return currentPage;
	}
}
