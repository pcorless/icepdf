/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */
public interface SeekableInput {
    //
    // Methods from InputStream
    // Since Java does not have multiple inheritance, we have to
    //  explicitly expose InputStream's methods as part of our interface
    //
    int read() throws IOException;

    int read(byte[] buffer) throws IOException;

    int read(byte[] buffer, int offset, int length) throws IOException;

    void close() throws IOException;

    int available();

    void mark(int readLimit);

    boolean markSupported();

    void reset() throws IOException;

    long skip(long n) throws IOException;


    //
    // Special methods that make this truly seekable
    //

    void seekAbsolute(long absolutePosition) throws IOException;

    void seekRelative(long relativeOffset) throws IOException;

    void seekEnd() throws IOException;

    long getAbsolutePosition() throws IOException;

    long getLength() throws IOException;

    // To access InputStream methods, call this instead of casting
    // This InputStream has to support mark(), reset(), and obviously markSupported()
    InputStream getInputStream();


    //
    // For regulating competing Threads' access to our state and I/O
    //

    void beginThreadAccess();

    void endThreadAccess();
}
