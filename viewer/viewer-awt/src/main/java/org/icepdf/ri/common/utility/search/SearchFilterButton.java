package org.icepdf.ri.common.utility.search;

import org.icepdf.ri.common.DropDownButton;
import org.icepdf.ri.common.PersistentJCheckBoxMenuItem;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import static org.icepdf.ri.util.ViewerPropertiesManager.*;

public class SearchFilterButton extends DropDownButton {

    private final JCheckBoxMenuItem wholePageCheckbox;
    private final JCheckBoxMenuItem wholeWordCheckbox;
    private final JCheckBoxMenuItem regexCheckbox;
    private final JCheckBoxMenuItem caseSensitiveCheckbox;
    private final JCheckBoxMenuItem cumulativeCheckbox;
    private final JCheckBoxMenuItem textCheckbox;
    private final JCheckBoxMenuItem formsCheckbox;
    private final JCheckBoxMenuItem commentsCheckbox;
    private final JCheckBoxMenuItem destinationsCheckbox;
    private final JCheckBoxMenuItem outlinesCheckbox;
    private final JCheckBoxMenuItem showPagesCheckbox;

    public SearchFilterButton(BaseSearchModel component, Controller controller, String titleRes) {
        super(controller,
                "",
                controller.getMessageBundle().getString(titleRes),
                "filter",
                controller.getPropertiesManager().getPreferences().get(PROPERTY_ICON_DEFAULT_SIZE, Images.SIZE_LARGE),
                SwingViewBuilder.buildButtonFont());
        final Preferences preferences = controller.getPropertiesManager().getPreferences();
        boolean isWholePage = preferences.getBoolean(PROPERTY_SEARCH_PANEL_WHOLE_PAGE_ENABLED, false);
        boolean isRegex = preferences.getBoolean(PROPERTY_SEARCH_PANEL_REGEX_ENABLED, true);
        boolean isWholeWord = preferences.getBoolean(PROPERTY_SEARCH_PANEL_WHOLE_WORDS_ENABLED, false);
        boolean isCaseSensitive = preferences.getBoolean(PROPERTY_SEARCH_PANEL_CASE_SENSITIVE_ENABLED, false);
        boolean isCumulative = preferences.getBoolean(PROPERTY_SEARCH_PANEL_CUMULATIVE_ENABLED, false);

        boolean isText = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_TEXT_ENABLED, true);
        boolean isForms = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_FORMS_ENABLED, true);
        boolean isComments = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_COMMENTS_ENABLED, false);
        boolean isDestinations = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_DEST_ENABLED, false);
        boolean isOutlines = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SEARCH_OUTLINES_ENABLED, false);

        boolean isShowPages = preferences.getBoolean(PROPERTY_SEARCH_PANEL_SHOW_PAGES_ENABLED, true);
        final ResourceBundle messageBundle = controller.getMessageBundle();
        wholePageCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.wholePageCheckbox.label"), isWholePage);
        wholePageCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_WHOLE_PAGE_ENABLED, isWholePage());
        });
        wholeWordCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.wholeWordCheckbox.label"), isWholeWord);
        wholeWordCheckbox.addActionListener(actionEvent -> {
            getRegexCheckbox().setEnabled(!isWholeWord());
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_WHOLE_WORDS_ENABLED, isWholeWord());
        });
        regexCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.regexCheckbox.label"), isRegex);
        regexCheckbox.addActionListener(actionEvent -> {
            getWholeWordCheckbox().setEnabled(!isRegex());
            wholePageCheckbox.setEnabled(!isRegex());
            wholePageCheckbox.setSelected(isWholePage() || isRegex());
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_REGEX_ENABLED, isRegex());
        });
        caseSensitiveCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.caseSenstiveCheckbox.label"), isCaseSensitive);
        caseSensitiveCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_SHOW_PAGES_ENABLED, isShowPages());
        });
        if (isRegex || isWholeWord) {
            regexCheckbox.setEnabled(isRegex);
            wholeWordCheckbox.setEnabled(isWholeWord);
        }
        cumulativeCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.cumlitiveCheckbox.label"), isCumulative);
        cumulativeCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_CUMULATIVE_ENABLED, isCumulative());
        });
        textCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.text.label"), isText);
        textCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_SEARCH_TEXT_ENABLED, isText());
        });
        formsCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.forms.label"), isText);
        formsCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_SEARCH_FORMS_ENABLED, isForms());
        });
        commentsCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.comments.label"), isComments);
        commentsCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_SEARCH_COMMENTS_ENABLED, isComments());
        });
        destinationsCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.destinations.label"), isDestinations);
        destinationsCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_SEARCH_DEST_ENABLED, isDestinations());
        });
        outlinesCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.outlines.label"), isOutlines);
        outlinesCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_SEARCH_OUTLINES_ENABLED, isOutlines());
        });
        showPagesCheckbox = new PersistentJCheckBoxMenuItem(messageBundle.getString(
                "viewer.utilityPane.search.showPagesCheckbox.label"), isShowPages);
        showPagesCheckbox.addActionListener(actionEvent -> {
            component.notifySearchFiltersChanged();
            preferences.putBoolean(PROPERTY_SEARCH_PANEL_SHOW_PAGES_ENABLED, isShowPages());
        });
        add(wholePageCheckbox);
        if (titleRes.contains("utilityPane")) {
            add(regexCheckbox);
            add(wholeWordCheckbox);
            add(caseSensitiveCheckbox);
            add(cumulativeCheckbox);
            addSeparator();
            add(textCheckbox);
            add(formsCheckbox);
            add(commentsCheckbox);
            add(outlinesCheckbox);
            add(destinationsCheckbox);
            addSeparator();
            add(showPagesCheckbox);
        } else {
            add(wholeWordCheckbox);
            add(caseSensitiveCheckbox);
            add(commentsCheckbox);
        }
    }

    public JCheckBoxMenuItem getWholePageCheckbox() {
        return wholePageCheckbox;
    }

    public JCheckBoxMenuItem getWholeWordCheckbox() {
        return wholeWordCheckbox;
    }

    public JCheckBoxMenuItem getRegexCheckbox() {
        return regexCheckbox;
    }

    public JCheckBoxMenuItem getCaseSensitiveCheckbox() {
        return caseSensitiveCheckbox;
    }

    public JCheckBoxMenuItem getCumulativeCheckbox() {
        return cumulativeCheckbox;
    }

    public JCheckBoxMenuItem getTextCheckbox() {
        return textCheckbox;
    }

    public JCheckBoxMenuItem getFormsCheckbox() {
        return formsCheckbox;
    }

    public JCheckBoxMenuItem getCommentsCheckbox() {
        return commentsCheckbox;
    }

    public JCheckBoxMenuItem getDestinationsCheckbox() {
        return destinationsCheckbox;
    }

    public JCheckBoxMenuItem getOutlinesCheckbox() {
        return outlinesCheckbox;
    }

    public JCheckBoxMenuItem getShowPagesCheckbox() {
        return showPagesCheckbox;
    }

    public boolean isWholePage() {
        return wholePageCheckbox.isSelected();
    }

    public boolean isWholeWord() {
        return wholeWordCheckbox.isSelected();
    }

    public boolean isRegex() {
        return regexCheckbox.isSelected();
    }

    public boolean isCaseSensitive() {
        return caseSensitiveCheckbox.isSelected();
    }

    public boolean isCumulative() {
        return cumulativeCheckbox.isSelected();
    }

    public boolean isText() {
        return textCheckbox.isSelected();
    }

    public boolean isForms() {
        return formsCheckbox.isSelected();
    }

    public boolean isComments() {
        return commentsCheckbox.isSelected();
    }

    public boolean isDestinations() {
        return destinationsCheckbox.isSelected();
    }

    public boolean isOutlines() {
        return outlinesCheckbox.isSelected();
    }

    public boolean isShowPages() {
        return showPagesCheckbox.isSelected();
    }

    public SearchTextTask getSearchTask(BaseSearchModel panel, Controller controller, String pattern) {
        SearchTextTask.Builder builder = new SearchTextTask.Builder(controller, pattern);
        return builder.setSearchModel(panel)
                .setWholePage(isWholePage())
                .setCaseSensitive(isCaseSensitive())
                .setWholeWord(isWholeWord())
                .setCumulative(isCumulative())
                .setShowPages(isShowPages())
                .setRegex(isRegex())
                .setText(isText())
                .setForms(isForms())
                .setDestinations(isDestinations())
                .setOutlines(isOutlines())
                .setComments(isComments()).build();
    }

    public SimpleSearchHelper getSimpleSearchHelper(Controller controller, String pattern) {
        SimpleSearchHelper.Builder builder = new SimpleSearchHelper.Builder(controller, pattern);
        return builder.setCaseSensitive(isCaseSensitive())
                .setWholePage(isWholePage())
                .setWholeWord(isWholeWord())
                .setComments(isComments()).build();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        Arrays.stream(getComponents()).forEach(c -> c.setEnabled(enabled));
    }
}
