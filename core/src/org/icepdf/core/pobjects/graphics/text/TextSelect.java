package org.icepdf.core.pobjects.graphics.text;

/**
 * Text select definitions.
 *
 * @since 4.0
 */
public interface TextSelect {

    public void clearSelected();

    public StringBuffer getSelected();

    public void clearHighlighted();

    public void selectAll();
//
//    public void deselectAll();
//
//    public void selectAllRight();
//
//    public void selectAllLeft();
//
//    public boolean isSelected();
//
//    public void setSelected(boolean selected);
}
