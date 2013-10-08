/*******************************************************************************
 * Copyright (c) 2013 Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Caroline McQuatt, Mike Lim - initial implementation
 *******************************************************************************/

package org.eclipse.cbi.maven.plugins.winsigner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

/**
 * Signs project main and attached artifact using
 * <a href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">Eclipse winsigner webservice</a>.
 *
 * @goal sign
 * @phase package
 * @requiresProject
 * @description runs the eclipse signing process
 */
public class SignMojo
    extends AbstractMojo
{
	/**
     * The signing service URL for signing Windows binaries
     *
     * <p>The signing service should return a signed exe file.</p>
     *
     * <p>The Official Eclipse signer service URL as described in the
     * <a href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">
     * wiki</a>.</p>
     *
     * <p><b>Configuration via Maven commandline</b></p>
     * <pre>-Dcbi.winsigner.signerUrl=http://localhost/winsign.php</pre>
     *
     * <p><b>Configuration via pom.xml</b></p>
     * <pre>{@code
     * <configuration>
     *   <signerUrl>http://localhost/winsign</signerUrl>
     * </configuration>
     * }</pre>
     *
     * @parameter expression="${cbi.winsigner.signerUrl}" default-value="http://build.eclipse.org:31338/winsign.php" )
     * @required
     * @since 1.0.4
     */
    private String signerUrl;

    /**
     * Maven build directory
     *
     * @parameter expression="${project.build.directory}"
     * @readonly
     * @since 1.0.4
     */
    private File workdir;

    /**
     * A list of full executable paths to be signed
     *
     * <p>If configured only these executables will be signed.</p>
     * <p><b><i>
     *    NOTE: If this is configured "baseSearchDir" and "fileNames"
     *    do NOT need to be configured.
     * </i></b></p>
     *
     * <p><b>Configuration via pom.xml</b></p>
     * <pre>{@code
     * <configuration>
     *   <signFiles>
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/win32/win32/x86/eclipse/eclipse.exe{@code</signFile>
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/win32/win32/x86/eclipse/eclipsec.exe{@code</signFile>
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/win32/win32/x86_64/eclipse/eclipse.exe{@code</signFile>
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/win32/win32/x86_64/eclipse/eclipsec.exe{@code</signFile>
     *   </signFiles>
     * </configuration>
     * }</pre>
     *
     * @parameter expression="${signFiles}"
     * @since 1.0.4
     */
    private String[] signFiles;

    /**
     * The base directory to search for executables to sign
     *
     * <p>If NOT configured baseSearchDir is ${project.build.directory}/products/</p>
     *
     * @parameter expression="${baseSearchDir}" default-value="${project.build.directory}/products/"
     * @since 1.0.4
     */
    private String baseSearchDir;

    /**
     * List of file names to sign
     *
     * <p>If NOT configured 'eclipse.exe' and 'eclipsec.exe' are signed.</p>
     *
     * @parameter expression="${fileNames}
     * @since 1.0.4
     */
    private String[] fileNames;

    /**
     * Continue the build even if signing fails
     *
     * <p><b>Configuration via Maven commandline</b></p>
     * <pre>-DcontinueOnFail=true</pre>
     *
     * <p><b>Configuration via pom.xml</b></p>
     * <pre>{@code
     * <configuration>
     *   <continueOnFail>true</continueOnFail>
     * </configuration>
     * }</pre>
     *
     * @parameter expression="${continueOnFail}" default-value="false"
     * @since 1.0.5
     */
    private boolean continueOnFail;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
    	//exe paths are configured
    	if (signFiles != null && !(signFiles.length == 0)) {
        	for (String path : signFiles) {
        		signArtifact(new File(path));
        	}
    	}
    	else { //perform search
        	if (fileNames == null || fileNames.length == 0) {
        		fileNames = new String[2];
        		fileNames[0] = "eclipse.exe";
        		fileNames[1] = "eclipsec.exe";
        	}

            File searchDir = new File(baseSearchDir);
            getLog().debug("Searching: " + searchDir);
            traverseDirectory(searchDir);
    	}
    }

    /**
     * Recursive method. Searches the base directory for files to sign.
     * @param files
     * @throws MojoExecutionException
     */
    private void traverseDirectory(File dir) throws MojoExecutionException {
    	if (dir.isDirectory()) {
    		getLog().debug("searching " + dir.getAbsolutePath());
    		for(File file : dir.listFiles()){
    			if (file.isFile()){
    				String fileName = file.getName();
    				for(String allowedName : fileNames) {
    					if (fileName.equals(allowedName)) {
    						signArtifact(file); // signs the file
    					}
    				}
    			} else if (file.isDirectory()) {
    				traverseDirectory(file);
    			}
    		}
    	}
    	else {
    		getLog().error("Internal error. " + dir + " is not a directory.");
    	}
    }

    /**
     * signs the file
     * @param file
     * @throws MojoExecutionException
     */
    protected void signArtifact( File file )
        throws MojoExecutionException
    {
        try
        {
            if ( !file.isFile() || !file.canRead())
            {
            	getLog().warn(file + " is either not a file or cannot be read, the artifact is not signed.");
                return; // Can't read this. Likely a directory.
            }

            final long start = System.currentTimeMillis();

            workdir.mkdirs();
            File tempSigned = File.createTempFile( file.getName(), ".signed-exe", workdir );
            try
            {
                postFile( file, tempSigned );
                if ( !tempSigned.canRead() || tempSigned.length() <= 0 )
                {
                    String msg = "Could not sign artifact " + file;

                    if (continueOnFail)
                    {
                        getLog().warn(msg);
                    }
                    else
                    {
                        throw new MojoExecutionException(msg);
                    }
                }
                FileUtils.copyFile( tempSigned, file );
            }
            finally
            {
                tempSigned.delete();
            }
            getLog().info( "Signed " + file + " in " + ( ( System.currentTimeMillis() - start ) / 1000 )
                               + " seconds." );
        }
        catch ( IOException e )
        {
            String msg = "Could not sign file " + file;

            if (continueOnFail)
            {
                getLog().warn(msg);
            }
            else
            {
                throw new MojoExecutionException(msg, e);
            }
        }
    }

    /**
     * helper to send the file to the signing service
     * @param source file to send
     * @param target file to copy response to
     * @throws IOException
     * @throws MojoExecutionException
     */
    private void postFile( File source, File target )
        throws IOException, MojoExecutionException
    {
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost( signerUrl );

        MultipartEntity reqEntity = new MultipartEntity();
        reqEntity.addPart( "file", new FileBody( source ) );
        post.setEntity( reqEntity );

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
        else
        {
            throw new MojoExecutionException( "Signer replied " + response.getStatusLine() );
        }
    }
}
