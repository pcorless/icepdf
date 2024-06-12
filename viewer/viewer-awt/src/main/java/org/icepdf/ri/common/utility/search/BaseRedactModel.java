package org.icepdf.ri.common.utility.search;

import java.text.MessageFormat;

public interface BaseRedactModel {

    void updateProgressControls(String message);

    MessageFormat setupRedactingMessageForm();

    MessageFormat setupRedactCompletionMessageForm();

}
