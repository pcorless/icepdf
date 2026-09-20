package org.icepdf.fx.ri.viewer.commands.view;

import org.icepdf.fx.ri.viewer.ViewerModel;
import org.icepdf.fx.ri.viewer.commands.Command;

/**
 * Command to rotate the document 90 degrees clockwise (right).
 */
public class RotateRightCommand implements Command {

    private final ViewerModel model;

    public RotateRightCommand(ViewerModel model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (model.document.get() != null) {
            double current = model.rotationAngle.get();
            double newRotation = (current + 90) % 360;
            model.rotationAngle.set(newRotation);
            model.statusMessage.set("Rotated right (90° CW)");
        }
    }
}

