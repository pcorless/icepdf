package org.icepdf.ri.common.properties;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.security.Permissions;
import org.icepdf.core.util.Utils;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class CustomPropertiesPanel extends JPanel {

    // layouts constraint
    private final GridBagConstraints constraints;
    private final Map<JTextField, JTextField> rows;
    private final PropertiesDialog dialog;
    private final Set<JTextField> invalids;

    public CustomPropertiesPanel(final Document document,
                                 final ResourceBundle messageBundle,
                                 final PropertiesDialog dialog) {
        this.dialog = dialog;
        // get information values if available
        final PInfo documentInfo = document.getInfo();
        constraints = new GridBagConstraints();
        rows = new HashMap<>();
        invalids = new HashSet<>();
        if (documentInfo != null) {

            setLayout(new GridBagLayout());
            setAlignmentY(JPanel.TOP_ALIGNMENT);

            constraints.fill = GridBagConstraints.NONE;
            constraints.weightx = 1.0;
            constraints.weighty = 1.0;
            constraints.anchor = GridBagConstraints.NORTHEAST;
            constraints.insets = new Insets(5, 5, 5, 5);

            final JPanel layoutPanel = new JPanel(new GridBagLayout());

            layoutPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                    messageBundle.getString("viewer.dialog.documentInformation.description.label"),
                    TitledBorder.LEFT,
                    TitledBorder.DEFAULT_POSITION));

            final boolean canModify = document.getSecurityManager() == null ||
                    document.getSecurityManager().getPermissions().getPermissions(Permissions.MODIFY_DOCUMENT);
            final Map<Object, Object> allCustomExtensions = documentInfo.getAllCustomExtensions();
            final Map<String, String> stringedExtensions = allCustomExtensions.entrySet().stream()
                    .collect(Collectors.toMap(e -> objToString(e.getKey(), document), e -> objToString(e.getValue(), document)));
            final List<String> keys = stringedExtensions.keySet().stream().sorted().collect(Collectors.toList());
            for (int i = 0; i < keys.size(); ++i) {
                addRow(layoutPanel, keys.get(i), stringedExtensions.get(keys.get(i)), canModify, i);
            }
            if (canModify) {
                constraints.fill = GridBagConstraints.NONE;
                constraints.anchor = GridBagConstraints.CENTER;
                final JButton addButton = new JButton("Add");
                addButton.addActionListener(e -> {
                    layoutPanel.remove(addButton);
                    final int i = layoutPanel.getComponentCount() / 3;
                    addRow(layoutPanel, "", "", true, i);
                    constraints.fill = GridBagConstraints.NONE;
                    constraints.anchor = GridBagConstraints.CENTER;
                    addGB(layoutPanel, addButton, 1, i + 1, 1, 1);
                    layoutPanel.revalidate();
                });
                addGB(layoutPanel, addButton, 1, keys.size(), 1, 1);
            }
            constraints.anchor = GridBagConstraints.NORTH;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(5, 5, 5, 5);
            addGB(this, layoutPanel, 0, 0, 1, 1);
        } else {
            add(new JLabel("No info"));
        }
    }

    Map<String, String> getProperties() {
        return rows.entrySet().stream().filter(e -> e.getKey().getText() != null && !e.getKey().getText().isEmpty())
                .collect(Collectors.toMap(e -> e.getKey().getText().trim(), e -> e.getValue().getText()));
    }

    private void addRow(final JPanel layoutPanel, final JTextField keyField, final JTextField valueField,
                        final JButton deleteButton, final int index) {
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 0.5;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTHEAST;
        constraints.insets = new Insets(0, 5, 5, 5);
        addGB(layoutPanel, keyField, 0, index, 1, 1);
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        addGB(layoutPanel, valueField, 1, index, 1, 1);
        if (deleteButton != null) {
            constraints.weightx = 0.25;
            constraints.anchor = GridBagConstraints.NORTHEAST;
            addGB(layoutPanel, deleteButton, 2, index, 1, 1);
        }
        rows.put(keyField, valueField);
    }

    private void addRow(final JPanel layoutPanel, final String key, final String value, final boolean canModify,
                        final int index) {
        final JTextField keyField = createTextField(key, canModify);
        keyField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent documentEvent) {
                verifyInput();
            }

            @Override
            public void removeUpdate(final DocumentEvent documentEvent) {
                verifyInput();
            }

            @Override
            public void changedUpdate(final DocumentEvent documentEvent) {
                verifyInput();
            }

            private void verifyInput() {
                final String input = keyField.getText().trim();
                final Set<String> allKeys = PInfo.ALL_COMMON_KEYS.stream().map(Name::getName)
                        .collect(Collectors.toSet());
                allKeys.addAll(rows.keySet().stream().filter(t -> t != keyField).map(JTextComponent::getText)
                        .collect(Collectors.toList()));
                final boolean isValid = input.isEmpty() || !allKeys.contains(input);
                keyField.setForeground(isValid ? Color.BLACK : Color.RED);
                if (isValid) {
                    invalids.remove(keyField);
                } else {
                    invalids.add(keyField);
                }
                dialog.setOkEnabled(invalids.isEmpty());
            }

        });
        addRow(layoutPanel, keyField, createTextField(value, canModify), canModify ?
                createDeleteButton(layoutPanel, index) : null, index);
    }

    private JButton createDeleteButton(final JPanel layoutPanel, final int index) {
        final JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> {
            final Component[] components = layoutPanel.getComponents();
            for (int j = index * 3; j < index * 3 + 3; ++j) {
                layoutPanel.remove(components[j]);
            }
            final JTextField keyField = (JTextField) components[index * 3];
            rows.remove(keyField);
            invalids.remove(keyField);
            final List<Component> toShift = Arrays.asList(Arrays.copyOfRange(components, index * 3 + 3,
                    components.length));
            toShift.forEach(layoutPanel::remove);
            for (int j = 0; j < toShift.size() - 1; j += 3) {
                addRow(layoutPanel, (JTextField) toShift.get(j), (JTextField) toShift.get(j + 1),
                        createDeleteButton(layoutPanel, index + j / 3), index + j / 3);
            }
            constraints.fill = GridBagConstraints.NONE;
            constraints.anchor = GridBagConstraints.CENTER;
            addGB(layoutPanel, toShift.get(toShift.size() - 1), 1, index + toShift.size() / 3 + 1, 1, 1);
            layoutPanel.revalidate();
        });
        return deleteButton;
    }

    private static String objToString(final Object object, final Document document) {
        if (object instanceof Name) {
            return ((Name) object).getName();
        } else if (object instanceof StringObject) {
            return Utils.convertStringObject(document.getCatalog().getLibrary(), (StringObject) object);
        } else {
            return object.toString();
        }
    }

    private static JTextField createTextField(final String content, final boolean canModify) {
        final JTextField textField = new JTextField(content);
        textField.setEnabled(canModify);
        return textField;
    }

    private void addGB(final JPanel layout, final Component component,
                       final int x, final int y,
                       final int rowSpan, final int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}