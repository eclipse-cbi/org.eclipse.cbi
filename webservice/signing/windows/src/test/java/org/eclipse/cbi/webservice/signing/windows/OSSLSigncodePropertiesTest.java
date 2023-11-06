/*******************************************************************************
 * Copyright (c) 2023 Eclipse Foundation and others
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cbi.webservice.signing.windows;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.eclipse.cbi.webservice.util.PropertiesReader;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class OSSLSigncodePropertiesTest {

    @Test
    public void multipleTimeServerURIs() throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final OSSLSigncodeProperties conf =
                    new OSSLSigncodeProperties(new PropertiesReader(createTestPropertiesWithMultipleTimeServerURIs(), fs));

            List<URI> timestampURIs = conf.getTimestampURIs();
            assertEquals(2, timestampURIs.size());

            assertEquals("http://timestamp.sectigo.com", timestampURIs.get(0).toString());
            assertEquals("http://timestamp.digicert.com", timestampURIs.get(1).toString());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void noTimeServerURI() throws IOException {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final OSSLSigncodeProperties conf =
                    new OSSLSigncodeProperties(new PropertiesReader(createEmptyTestProperties(), fs));

            conf.getTimestampURIs();
            fail();
        }
    }

    private static Properties createEmptyTestProperties() {
        return new Properties();
    }

    private static Properties createTestPropertiesWithMultipleTimeServerURIs() {
        Properties properties = new Properties();
        properties.setProperty("windows.osslsigncode.timestampurl.1", "http://timestamp.sectigo.com");
        properties.setProperty("windows.osslsigncode.timestampurl.2", "http://timestamp.digicert.com");
        return properties;
    }
}
