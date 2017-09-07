package org.icepdf.ri.common.preferences;

public class BooleanItem {
    private String label;
    private boolean value;

    public BooleanItem(String label, boolean enabled) {
        this.label = label;
        this.value = enabled;
    }

    public String getLabel() {
        return label;
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object imageReferenceItem) {
        return imageReferenceItem.equals(value);
    }

}