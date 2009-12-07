package org.icepdf.core.io;

import java.io.OutputStream;
import java.io.IOException;

/**
 * Keeps track of how many bytes have been written out
 * @since 4.0
 */

public class CountingOutputStream extends OutputStream {
    private OutputStream wrapped;
    private long count;

    public CountingOutputStream(OutputStream wrap) {
        wrapped = wrap;
        count = 0L;
    }

    public long getCount() {
        return count;
    }

    public void write(int i) throws IOException {
        wrapped.write(i);
        count++;
    }

    public void write(byte[] bytes) throws IOException {
        wrapped.write(bytes);
        count += bytes.length;
    }

    public void write(byte[] bytes, int offset, int len) throws IOException {
        wrapped.write(bytes, offset, len);
        int num = Math.min(len, bytes.length - offset);
        count += num;
    }

    public void flush() throws IOException {
        wrapped.flush();
    }

    public void close() throws IOException {
        wrapped.close();
    }
}
