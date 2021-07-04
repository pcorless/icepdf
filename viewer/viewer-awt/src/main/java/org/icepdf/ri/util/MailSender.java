package org.icepdf.ri.util;

import org.icepdf.core.pobjects.Document;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MailSender {

    private static final Logger logger = Logger.getLogger(MailSender.class.toString());

    private MailSender() {
    }

    /**
     * Backup for sendMail(), using a mailto uri
     *
     * @param os         The name of the operating system
     * @param attachment the path of the file to attach
     * @param controller The document controller
     */
    private static void sendMailMailto(String os, String attachment, Controller controller) {
        Component viewer = controller.getViewerFrame();
        ResourceBundle messageBundle = controller.getMessageBundle();
        String mailto = "mailto:?attachment=" + attachment;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                URI mailtoURI = new URI(mailto.replace(" ", "%20"));
                Desktop.getDesktop().mail(mailtoURI);
            } else {
                java.util.List<String> args = new ArrayList<>();
                if (os.contains("win")) {
                    args.add("cmd.exe");
                    args.add("/c");
                    args.add("start");
                    args.add(mailto.replace(" ", "%20"));
                } else if (os.contains("osx")) {
                    args.add("open");
                    args.add(mailto.replace(" ", "%20"));
                } else if (os.contains("nix") || os.contains("aix") || os.contains("nux")) {
                    args.add("bash");
                    args.add("-c");
                    args.add("xdg-open " + mailto.replace(" ", "%20"));
                } else {
                    logger.warning("Unsupported os : " + os);
                    showErrorMessage(messageBundle, viewer, "unsupported");
                }
                if (!args.isEmpty()) {
                    final String[] argsA = args.toArray(new String[]{});
                    Process process = new ProcessBuilder(argsA).start();
                    if (process.exitValue() != 0) {
                        showErrorMessage(messageBundle, viewer, "error");
                    }
                }
            }
        } catch (URISyntaxException | IOException e) {
            logger.log(Level.WARNING, "Error using " + mailto, e);
            showErrorMessage(messageBundle, viewer, "error");
        }
    }

    private static void showErrorMessage(ResourceBundle messageBundle, Component viewer, String error) {
        JOptionPane.showMessageDialog(viewer,
                messageBundle.getString("viewer.dialog.sendmail." + error + ".msg"),
                messageBundle.getString("viewer.dialog.sendmail." + error + ".title"),
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Sends the current document by mail, opening the default mail client
     *
     * @param controller The document controller
     */
    public static void sendMail(Controller controller) {
        Component viewer = controller.getViewerFrame();
        ResourceBundle messageBundle = controller.getMessageBundle();
        Document document = controller.getDocument();
        String os = System.getProperty("os.name").toLowerCase();
        String attachment = document.getDocumentLocation();
        List<String> args = new ArrayList<>();
        if (os.contains("win")) {
            try {
                String mailKey = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\Mail";
                String[] value = WindowsRegistry.readRegistry(mailKey, "");
                if (value[10].toLowerCase().contains("thunderbird")) {
                    String thunderbirdKey = mailKey + "\\Mozilla Thunderbird\\shell\\open\\command";
                    String[] pfad = WindowsRegistry.readRegistry(thunderbirdKey, "");
                    args.add(pfad[10]);
                    args.add(pfad[11]);
                    args.add("/compose");
                    args.add("attachment='" + attachment + "'");
                } else if (value[10].toLowerCase().contains("outlook")) {
                    String outlookKey = mailKey + "\\Microsoft Outlook\\shell\\open\\command";
                    String[] pfad = WindowsRegistry.readRegistry(outlookKey, "");
                    args.add(pfad[10]);
                    args.add("/a");
                    args.add(attachment);
                }
            } catch (Exception ignored) {
            }
        } else if (os.contains("nix") || os.contains("aix") || os.contains("nux")) {
            try {
                String[] mimeArgs = {"xdg-mime", "query", "default", "x-scheme-handler/mailto"};
                Process mimeProc = new ProcessBuilder(mimeArgs).start();
                Scanner scanner = new Scanner(mimeProc.getInputStream());
                String app = scanner.nextLine();
                if (app.toLowerCase().contains("thunderbird")) {
                    args.add("thunderbird");
                    args.add("-compose");
                    args.add("attachment='" + attachment + "'");
                }
                scanner.close();
            } catch (IOException ignored) {
            }
        }
        if (args.isEmpty()) {
            sendMailMailto(os, attachment, controller);
        } else {
            Process process;
            try {
                process = new ProcessBuilder(args.toArray(new String[]{})).start();
                process.waitFor(1, TimeUnit.SECONDS);
                if (!process.isAlive() && process.exitValue() != 0) {
                    sendMailMailto(os, attachment, controller);
                }
            } catch (IOException | InterruptedException e) {
                sendMailMailto(os, attachment, controller);
            }
        }
    }

}
