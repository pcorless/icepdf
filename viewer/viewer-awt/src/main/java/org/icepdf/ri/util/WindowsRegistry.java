/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.ri.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

/**
 * Class used to retrieve information from the Windows registry
 * See https://stackoverflow.com/questions/62289/read-write-to-windows-registry-using-java
 */
public final class WindowsRegistry {

    private WindowsRegistry() {
    }

    /**
     * Gets a registry value
     *
     * @param location path in the registry
     * @param key      registry key
     * @return registry value or null if not found
     */
    public static String[] readRegistry(String location, String key) {
        try {
            // Run reg query, then read output with StreamReader (internal class)
            Process process = Runtime.getRuntime().exec("reg query " +
                    '"' + location);
            StreamReader reader = new StreamReader(process.getInputStream());
            reader.start();
            process.waitFor();
            reader.join();
            // Parse out the value
            String[] parsed = reader.getResult().split("\\s+");
            if (parsed.length > 1) {
                return parsed;
            }
        } catch (Exception e) {
        }

        return null;
    }

    static class StreamReader extends Thread {
        private final InputStream is;
        private final StringWriter sw = new StringWriter();

        public StreamReader(InputStream is) {
            this.is = is;
        }

        public void run() {
            try {
                int c;
                while ((c = is.read()) != -1)
                    sw.write(c);
            } catch (IOException e) {
            }
        }

        public String getResult() {
            return sw.toString();
        }
    }
}
