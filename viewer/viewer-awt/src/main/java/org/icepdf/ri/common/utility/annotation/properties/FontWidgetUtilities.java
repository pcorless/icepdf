package org.icepdf.ri.common.utility.annotation.properties;

import java.util.ResourceBundle;

public class FontWidgetUtilities {
    public static ValueLabelItem[] generateFontNameList(ResourceBundle messageBundle) {
        return new ValueLabelItem[]{
                new ValueLabelItem("Helvetica",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.helvetica")),
                new ValueLabelItem("Helvetica-Oblique",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.helveticaOblique")),
                new ValueLabelItem("Helvetica-Bold",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.helveticaBold")),
                new ValueLabelItem("Helvetica-BoldOblique",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name" +
                                ".HelveticaBoldOblique")),
                new ValueLabelItem("Times-Italic",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.timesItalic")),
                new ValueLabelItem("Times-Bold",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.timesBold")),
                new ValueLabelItem("Times-BoldItalic",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.timesBoldItalic")),
                new ValueLabelItem("Times-Roman",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.timesRoman")),
                new ValueLabelItem("Courier",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.courier")),
                new ValueLabelItem("Courier-Oblique",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.courierOblique")),
                new ValueLabelItem("Courier-BoldOblique",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.courierBoldOblique")),
                new ValueLabelItem("Courier-Bold",
                        messageBundle.getString("viewer.utilityPane.annotation.freeText.font.name.courierBold"))};
    }

    public static ValueLabelItem[] generateFontSizeNameList(ResourceBundle messageBundle) {
        return new ValueLabelItem[]{
                new ValueLabelItem(6, messageBundle.getString("viewer.common.number.six")),
                new ValueLabelItem(8, messageBundle.getString("viewer.common.number.eight")),
                new ValueLabelItem(9, messageBundle.getString("viewer.common.number.nine")),
                new ValueLabelItem(10, messageBundle.getString("viewer.common.number.ten")),
                new ValueLabelItem(11, messageBundle.getString("viewer.common.number.eleven")),
                new ValueLabelItem(12, messageBundle.getString("viewer.common.number.twelve")),
                new ValueLabelItem(14, messageBundle.getString("viewer.common.number.fourteen")),
                new ValueLabelItem(16, messageBundle.getString("viewer.common.number.sixteen")),
                new ValueLabelItem(18, messageBundle.getString("viewer.common.number.eighteen")),
                new ValueLabelItem(20, messageBundle.getString("viewer.common.number.twenty")),
                new ValueLabelItem(24, messageBundle.getString("viewer.common.number.twentyFour")),
                new ValueLabelItem(36, messageBundle.getString("viewer.common.number.thirtySix")),
                new ValueLabelItem(48, messageBundle.getString("viewer.common.number.fortyEight"))};
    }
}
