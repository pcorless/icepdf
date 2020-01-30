package org.icepdf.ri.common.utility.search;

import java.text.MessageFormat;

public interface BaseSearchComponent {
    void updateProgressControls(String message);

    void addFoundCommentEntry(SearchTextTask.CommentsResult outlineResult, SearchTextTask searchTextTask);

    void addFoundOutlineEntry(SearchTextTask.OutlineResult outlineResult, SearchTextTask searchTextTask);

    void addFoundTextEntry(SearchTextTask.TextResult outlineResult, SearchTextTask searchTextTask);

    void addFoundDestinationEntry(SearchTextTask.DestinationsResult outlineResult, SearchTextTask searchTextTask);

    MessageFormat setupSearchingMessageForm();

    MessageFormat setupSearchResultMessageForm();

    MessageFormat setupSearchCompletionMessageForm();

    void notifySearchFiltersChanged();
}
