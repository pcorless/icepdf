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
* NavigationToolbar.java
* ---------------
*/
package org.jpedal.jbig2.examples.viewer;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToolBar;

import org.jpedal.jbig2.JBIG2Decoder;

public class NavigationToolbar extends JToolBar {
	protected static final int FIRSTPAGE = 0;
	protected static final int FBACKPAGE = 1;
	protected static final int BACKPAGE = 2;
	protected static final int FORWARDPAGE = 3;
	protected static final int FFORWARDPAGE = 4;
	protected static final int LASTPAGE = 5;
	protected static final int SETPAGE = 6;

	protected JTextField currentPageBox = new JTextField(4);
	private JLabel totalNoOfPages = new JLabel();
	private JBIG2Viewer viewer;

	public NavigationToolbar(JBIG2Viewer viewer) {

		this.viewer = viewer;

		totalNoOfPages.setText("of 1");
		currentPageBox.setText("1");

		add(Box.createHorizontalGlue());

		addButton("Rewind To Start", "/org/jpedal/jbig2/examples/viewer/res/start.gif", FIRSTPAGE);
		addButton("Back 10 Pages", "/org/jpedal/jbig2/examples/viewer/res/fback.gif", FBACKPAGE);
		addButton("Back", "/org/jpedal/jbig2/examples/viewer/res/back.gif", BACKPAGE);

		add(new JLabel("Page"));
		currentPageBox.setMaximumSize(new Dimension(5, 50));
		currentPageBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				executeCommand(SETPAGE);
			}
		});
		add(currentPageBox);
		add(totalNoOfPages);

		addButton("Forward", "/org/jpedal/jbig2/examples/viewer/res/forward.gif", FORWARDPAGE);
		addButton("Forward 10 Pages", "/org/jpedal/jbig2/examples/viewer/res/fforward.gif", FFORWARDPAGE);
		addButton("Fast Forward To End", "/org/jpedal/jbig2/examples/viewer/res/end.gif", LASTPAGE);

		add(Box.createHorizontalGlue());

//		close();
	}

	public void setTotalNoOfPages(int noOfPages) {
		totalNoOfPages.setText("of " + noOfPages);
	}

	public void setCurrentPage(int currentPage) {
		currentPageBox.setText(String.valueOf(currentPage));
	}

    private void addButton(String tooltip, String url, final int type) {
		JButton button = new JButton();
		button.setIcon(new ImageIcon(getClass().getResource(url)));
		button.setToolTipText(tooltip);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				executeCommand(type);
			}
		});

		add(button);
	}

	public void executeCommand(int type) {
		JBIG2Decoder decoder = viewer.getDecoder();
		switch (type) {
		case FIRSTPAGE:
			viewer.displayPage(1);
			break;
		case FBACKPAGE:
			viewer.displayPage(viewer.getCurrentPage() - 10);
			break;
		case BACKPAGE:
			viewer.displayPage(viewer.getCurrentPage() - 1);
			break;
		case FORWARDPAGE:
			viewer.displayPage(viewer.getCurrentPage() + 1);
			break;
		case FFORWARDPAGE:
			viewer.displayPage(viewer.getCurrentPage() + 10);
			break;
		case LASTPAGE:
			viewer.displayPage(decoder.getNumberOfPages());
			break;
		case SETPAGE:
			int page = -1;
			try {
				page = Integer.parseInt(currentPageBox.getText());
			} catch (NumberFormatException e) {
			}

			if (page >= 1 && page <= decoder.getNumberOfPages()) {
				viewer.displayPage(page);
			} else {
				currentPageBox.setText(String.valueOf(viewer.getCurrentPage()));
			}
			break;
		}
	}
}
