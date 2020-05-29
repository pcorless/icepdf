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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DavFileClient {

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

    public DavFileClient(final String url) {
        this(url, null, null);
    }

    public DavFileClient(final String url, final String username, final String password) {
        this(url, username, password, false);
    }

    public DavFileClient(final String url, final String username, final String password, final boolean readOnly) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.sardine = username == null || password == null ? SardineFactory.begin() : SardineFactory.begin(username, password);
        final String[] split = url.split("/");
        folderUrl = Arrays.stream(split).limit(split.length - 1).collect(Collectors.joining("/"));
        final String[] names = split[split.length - 1].split("\\.");
        name = names[0];
        ext = names[1];
        this.readOnly = readOnly;
    }

    public List<DavResource> getFolderContents() throws IOException {
        return sardine.list(folderUrl);
    }

    public File getFile() throws IOException {
        if (file == null) {
            file = File.createTempFile(name, "." + ext);
            try (final InputStream stream = sardine.get(url)) {
                Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                mimeType = new Tika().detect(file);
            }
        }
        return file;
    }

    public InputStream getStream() throws IOException {
        if (stream == null || stream.available() == 0) {
            this.stream = sardine.get(url);
        }
        return stream;
    }

    public void save(final InputStream inputStream) throws IOException {
        if (!readOnly) {
            if (inputStream.markSupported()) {
                mimeType = new Tika().detect(stream);
            }
            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                mimeType = new Tika().detect(name + "." + ext);
            }
            final String newUrl = folderUrl + "/" + name + "." + ext;
            sardine.put(newUrl, inputStream, mimeType);
            revision += 1;
        }
    }

    public void delete() throws IOException {
        if (!readOnly) {
            sardine.delete(url);
        }
    }

    public boolean exists() throws IOException {
        return sardine.exists(url);
    }

    public String getName() {
        return name;
    }

    public String getExt() {
        return ext;
    }

    public int getRevision() {
        return revision;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFolderUrl() {
        return folderUrl;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
        sardine.setCredentials(username, password);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
        sardine.setCredentials(username, password);
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
