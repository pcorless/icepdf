package org.icepdf.qa.viewer.project;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import org.icepdf.qa.viewer.common.Mediator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Contains a simple console view that take System.out as a source.  The TextArea has proven to be a little slow as
 * it get busy.  Sor the current work around is to do a little ascii art.
 */
public class ProjectUtilityPane extends TabPane {

    private Tab consoleTab;

    private TextArea consoleTextArea;
    private PrintStream printStream;
    private int count;

    public ProjectUtilityPane(Mediator mediator) {
        super();
        setSide(Side.TOP);

        consoleTab = new Tab("Console");
        consoleTab.setClosable(false);

        consoleTextArea = new TextArea();
        printStream = new PrintStream(new Console(consoleTextArea));

        System.setOut(printStream);
//        System.setErr(printStream);
        System.out.println("Console logger initialized");

        consoleTab.setContent(consoleTextArea);
        getTabs().addAll(consoleTab);
    }

    public void clearConsole() {
        consoleTextArea.clear();
        count = 0;
    }

    public class Console extends OutputStream {
        private TextArea console;

        public Console(TextArea console) {
            this.console = console;
        }

        public void appendText(String valueOf) {
            Platform.runLater(() -> console.appendText(valueOf));
        }

        public void write(int b) throws IOException {
            if (count % 80 == 0) {
                appendText(String.valueOf('\n'));
            }
            appendText(String.valueOf((char) b));
            // line break our ascii art at 80.
            if (b == '=' || b == '|' || b == '~') {
                count++;
            } else {
                count = 1;
            }
        }
    }
}