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
package org.icepdf.ri.common;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataHandler;
import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Great little implementation that does the lifting to allow for drag and drop rows in using a list.  This is
 * so much easier in JavaFx...
 *
 * @since 6.3
 */
public class ListItemTransferHandler extends TransferHandler {

    private final DataFlavor dataFlavor;

    private int[] indices;
    //Location where items were added
    private int addIndex = -1;
    //Number of items added.
    private int addCount;

    public ListItemTransferHandler() {
        this.dataFlavor = new ActivationDataFlavor(
                Object[].class, DataFlavor.javaJVMLocalObjectMimeType, "Array of items");
    }

    @Override
    protected Transferable createTransferable(JComponent component) {
        JList<Object> list = (JList<Object>) component;
        indices = list.getSelectedIndices();
        Object[] transferObjects = list.getSelectedValuesList().toArray();
        return new DataHandler(transferObjects, dataFlavor.getMimeType());
    }

    @Override
    public boolean canImport(TransferSupport info) {
        return info.isDrop() && info.isDataFlavorSupported(dataFlavor);
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    public boolean importData(TransferSupport info) {
        if (!canImport(info))
            return false;

        JList<Object> target = (JList<Object>) info.getComponent();
        JList.DropLocation dl = (JList.DropLocation) info.getDropLocation();
        DefaultListModel<Object> listModel = (DefaultListModel<Object>) target.getModel();
        int index = dl.getIndex();
        int max = listModel.getSize();

        if (index < 0 || index > max) {
            index = max;
        }

        addIndex = index;

        try {
            Object[] values = (Object[]) info.getTransferable().getTransferData(dataFlavor);
            addCount = values.length;
            for (Object value : values) {
                int idx = index++;
                listModel.add(idx, value);
                target.addSelectionInterval(idx, idx);
            }
            return true;
        } catch (UnsupportedFlavorException | IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    protected void exportDone(JComponent c, Transferable data, int action) {
        cleanup(c, action == MOVE);
    }

    // update the list model with the new data order.
    private void cleanup(JComponent component, boolean remove) {
        if (remove && indices != null) {
            JList<Object> source = (JList<Object>) component;
            DefaultListModel<Object> model = (DefaultListModel<Object>) source.getModel();
            if (addCount > 0) {
                for (int i = 0; i < indices.length; i++) {
                    if (indices[i] >= addIndex)
                        indices[i] += addCount;
                }
            }
            for (int i = indices.length - 1; i >= 0; i--) {
                model.remove(indices[i]);
            }
        }
        indices = null;
        addCount = 0;
        addIndex = -1;
    }

}
