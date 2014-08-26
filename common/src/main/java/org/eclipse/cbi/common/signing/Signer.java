/*******************************************************************************
 * Copyright (c) 2014 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Thanh Ha - initial implementation
 *******************************************************************************/

package org.eclipse.cbi.common.signing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

/**
 * Utility class providing common functions related to signing.
 */
public class Signer
{
    /*
     * Signs a file using a web based signing service
     *
     * @param source the source file to send to the signing service
     * @param target the destination to save the signed file to
     * @param signerUrl the URL of the signing service
     */
    public static void signFile( File source, File target, String signerUrl )
            throws IOException, MojoExecutionException
    {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost( signerUrl );

        MultipartEntityBuilder builder = MultipartEntityBuilder.create(); 
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

        builder.addPart( "file", new FileBody( source ) );
        post.setEntity( builder.build() );


        HttpResponse response = client.execute( post );
        int statusCode = response.getStatusLine().getStatusCode();

        HttpEntity resEntity = response.getEntity();

        if ( statusCode >= 200 && statusCode <= 299 && resEntity != null )
        {
            InputStream is = resEntity.getContent();
            try
            {
                FileUtils.copyStreamToFile( new RawInputStreamFacade( is ), target );
            }
            finally
            {
                IOUtil.close( is );
            }
        }
        else if ( statusCode >= 500 && statusCode <= 599)
        {
            InputStream is = resEntity.getContent();
            String message = IOUtil.toString(is, "UTF-8");
            throw new NoHttpResponseException( "Server failed with " + message );
        }
        else
        {
            throw new MojoExecutionException( "Signer replied " + response.getStatusLine() );
        }
    }
}
