package org.icepdf.ri.util;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import org.apache.tika.Tika;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Represents a WebDav connection to a file
 */
public class DavFileClient implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(DavFileClient.class.getName());
    private final Sardine sardine;
    private final String url;
    private final String folderUrl;
    private final String name;
    private final String ext;
    private final boolean readOnly;
    private String username;
    private String password;
    private int revision = 0;
    private File file;
    private InputStream stream;
    private String mimeType;
    private String lock = null;

    /**
     * Instantiates a client with the given url and no credentials
     *
     * @param url The url
     */
    public DavFileClient(final String url) {
        this(url, null, null);
    }

    /**
     * Instantiates a client with the given url and credentials
     *
     * @param url      The url
     * @param username The username
     * @param password The password
     */
    public DavFileClient(final String url, final String username, final String password) {
        this(url, username, password, false);
    }

    /**
     * Instantiates a client with the given url and credentials
     *
     * @param url      The url
     * @param username The username
     * @param password The password
     * @param readOnly Whether the client is readonly or not
     */
    public DavFileClient(final String url, final String username, final String password, final boolean readOnly) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.sardine = username == null || password == null ? SardineFactory.begin() : SardineFactory.begin(username, password);
        final String[] split = url.split("/");
        folderUrl = Arrays.stream(split).limit(split.length - 1L).collect(Collectors.joining("/"));
        final String[] names = split[split.length - 1].split("\\.");
        name = names[0];
        if (names.length == 1) {
            ext = "";
        } else {
            ext = names[1];
        }
        this.readOnly = readOnly;
        setPreemptiveAuthenticationEnabled(true);
    }

    /**
     * Sets the preemptive authentication value
     * Must be set to true if an action causes a NonRepeatableRequestException
     *
     * @param enabled true or false
     */
    public void setPreemptiveAuthenticationEnabled(final boolean enabled) {
        if (enabled) {
            sardine.enablePreemptiveAuthentication(url);
        } else {
            sardine.disablePreemptiveAuthentication();
        }
    }

    /**
     * @return The contents of the parent folder (including the current file)
     * @throws IOException
     */
    public List<DavResource> getParentFolderContents() throws IOException {
        final String[] split = folderUrl.split("/");
        final String folderName = split[split.length - 1].isEmpty() ? split[split.length - 2] : split[split.length - 1];
        return sardine.list(folderUrl).stream().filter(dr -> !dr.getName().equals(folderName)).collect(Collectors.toList());
    }

    /**
     * @return A temporary copy file for this connection
     * @throws IOException
     */
    public File getFile() throws IOException {
        if (file == null) {
            file = File.createTempFile(name, ext.isEmpty() ? "" : "." + ext);
            try (final InputStream in = sardine.get(url)) {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                mimeType = new Tika().detect(file);
            }
        }
        return file;
    }

    /**
     * @return The content stream of this connection
     * @throws IOException
     */
    public InputStream getStream() throws IOException {
        resetStream();
        return stream;
    }

    /**
     * Resets the stream of this connection
     *
     * @throws IOException
     */
    public void resetStream() throws IOException {
        close();
        if (exists()) {
            stream = sardine.get(url);
        }
    }

    /**
     * Saves the given inputstream to the remote url
     *
     * @param inputStream The data to save
     * @throws IOException
     */
    public void save(final InputStream inputStream) throws IOException {
        if (!readOnly) {
            if (inputStream.markSupported() && inputStream.available() > 0) {
                mimeType = new Tika().detect(inputStream);
            }
            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                mimeType = new Tika().detect(name + (ext.isEmpty() ? "" : "." + ext));
            }
            createDirectoryHierarchy(folderUrl);
            sardine.put(url, inputStream, mimeType);
            revision += 1;
        }
    }


    private void createDirectoryHierarchy(final String url) throws IOException {
        if (!sardine.exists(url)) {
            final boolean isHttps = url.startsWith("https");
            final String startUrl = isHttps ? "https://" : "http://";
            final List<String> folderUrls = Arrays.asList(url.substring(isHttps ? 8 : 7).split("/"));
            int startIdx = folderUrls.size();
            while (startIdx > 0) {
                final String parentFolder = startUrl + String.join("/", folderUrls.subList(0, startIdx));
                if (sardine.exists(parentFolder)) {
                    break;
                }
                startIdx--;
            }
            if (startIdx < folderUrls.size()) {
                for (int idx = startIdx + 1; idx <= folderUrls.size(); ++idx) {
                    final String toCreate = startUrl + String.join("/", folderUrls.subList(0, idx));
                    sardine.createDirectory(toCreate);
                }
            }
        }
    }

    /**
     * Closes the underlying stream
     *
     * @throws IOException
     */
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
    }

    /**
     * Deletes the resource
     *
     * @throws IOException
     */
    public void delete() throws IOException {
        if (!readOnly && exists()) {
            sardine.delete(url);
        }
    }

    /**
     * @return Whether the remote file exists or not
     * @throws IOException
     */
    public boolean exists() throws IOException {
        return sardine.exists(url);
    }

    /**
     * Renames the remote resource to the given name. No-op if the file doesn't exist.
     *
     * @param newName The new name
     * @return The client managing the resource under the new name
     * @throws IOException
     */
    public DavFileClient rename(final String newName) throws IOException {
        return move(folderUrl + "/" + newName);
    }

    /**
     * Moves the remote resource to the given url. No-op if the file doesn't exist.
     *
     * @param newUrl The new url
     * @return The client managing the resource under the new url
     * @throws IOException
     */
    public DavFileClient move(final String newUrl) throws IOException {
        return move(newUrl, true);
    }

    /**
     * Moves the remote resource to the given url. No-op if the file doesn't exist.
     *
     * @param newUrl    The new url
     * @param overwrite Whether to overwrite the resource or not
     * @return The client managing the resource under the new url
     * @throws IOException
     */
    public DavFileClient move(final String newUrl, final boolean overwrite) throws IOException {
        if (!readOnly && exists()) {
            final String[] split = newUrl.split("/");
            createDirectoryHierarchy(Arrays.stream(split).limit(split.length - 1L).collect(Collectors.joining("/")));
            sardine.move(url, newUrl, overwrite);
            return new DavFileClient(newUrl, username, password);
        } else {
            return this;
        }
    }

    /**
     * Copies the resource to the given url. No-op if the file doesn't exist.
     *
     * @param newUrl The new url
     * @return The client managing the new resource
     * @throws IOException
     */
    public DavFileClient copy(final String newUrl) throws IOException {
        return copy(newUrl, true);
    }

    /**
     * Copies the resource to the given url. No-op if the file doesn't exist.
     *
     * @param newUrl    The new url
     * @param overwrite Whether to overwrite or not
     * @return The client managing the new resource
     * @throws IOException
     */
    public DavFileClient copy(final String newUrl, final boolean overwrite) throws IOException {
        if (exists()) {
            final String[] split = newUrl.split("/");
            createDirectoryHierarchy(Arrays.stream(split).limit(split.length - 1L).collect(Collectors.joining("/")));
            sardine.copy(url, newUrl, overwrite);
            return new DavFileClient(newUrl, username, password);
        } else {
            return this;
        }
    }

    /**
     * Creates the directory
     *
     * @throws IOException
     */
    public void createDirectory() throws IOException {
        if (!readOnly) {
            createDirectoryHierarchy(url);
        }
    }

    /**
     * @return The name of the file
     */
    public String getName() {
        return name;
    }

    /**
     * @return The extension of the file
     */
    public String getExt() {
        return ext;
    }

    /**
     * @return The revision of the file
     */
    public int getRevision() {
        return revision;
    }

    /**
     * @return The mimetype of the file
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * @return The parent folder url
     */
    public String getFolderUrl() {
        return folderUrl;
    }

    /**
     * @return The url of the resource
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return The username for the connection
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username for this connection
     *
     * @param username The username
     */
    public void setUsername(final String username) {
        this.username = username;
        sardine.setCredentials(username, password);
    }

    /**
     * @return The password for the connection
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for this connection
     *
     * @param password The password
     */
    public void setPassword(final String password) {
        this.password = password;
        sardine.setCredentials(username, password);
    }

    /**
     * @return Whether the connection is readonly or not
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Locks the resource
     */
    public void lock() {
        try {
            this.lock = sardine.lock(url);
        } catch (final IOException e) {
            logger.log(Level.WARNING, "Error locking " + url, e);
        }
    }

    /**
     * Unlocks the resource
     */
    public void unlock() {
        if (lock != null) {
            try {
                sardine.unlock(url, lock);
            } catch (final IOException e) {
                logger.log(Level.WARNING, "Error unlocking " + url, e);
            }
            lock = null;
        }
    }

    /**
     * @return The modification time
     */
    public LocalDateTime getModificationTime() throws IOException {
        return getLocalDateTime(DavResource::getModified);
    }

    /**
     * @return The creation time
     */
    public LocalDateTime getCreationTime() throws IOException {
        return getLocalDateTime(DavResource::getCreation);
    }

    private LocalDateTime getLocalDateTime(final Function<DavResource, Date> resourceToDate) throws IOException {
        final String slashedFolder = folderUrl.endsWith("/") ? folderUrl : folderUrl + "/";
        final List<DavResource> resources = sardine.list(folderUrl);
        return resources.stream().filter(dr -> (slashedFolder + dr.getName()).equals(url)).findFirst().map(dr -> resourceToLocalDateTime(dr, resourceToDate)).orElse(null);
    }

    private static LocalDateTime resourceToLocalDateTime(final DavResource resource, final Function<DavResource, Date> resourceToDate) {
        final Date date = resourceToDate.apply(resource);
        if (date != null) {
            return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } else {
            return null;
        }
    }
}