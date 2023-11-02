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

import org.eclipse.cbi.webservice.util.PropertiesReader;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class OSSLSigncodePropertiesTest {

    @Test
    public void multipleTimeServerURIs() throws IOException, URISyntaxException {
        URL resource = this.getClass().getResource("/multiple-timeservers.properties");
        assertNotNull(resource);
        final OSSLSigncodeProperties conf = new OSSLSigncodeProperties(PropertiesReader.create(Path.of(resource.toURI())));

        List<URI> timestampURIs = conf.getTimestampURIs();
        assertEquals(2, timestampURIs.size());

        assertEquals("http://timestamp.sectigo.com", timestampURIs.get(0).toString());
        assertEquals("http://timestamp.digicert.com", timestampURIs.get(1).toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void noTimeServerURI() throws IOException, URISyntaxException {
        URL resource = this.getClass().getResource("/no-timeserver.properties");
        assertNotNull(resource);
        final OSSLSigncodeProperties conf = new OSSLSigncodeProperties(PropertiesReader.create(Path.of(resource.toURI())));

        List<URI> timeServerURIs = conf.getTimestampURIs();
        fail();
    }

}
