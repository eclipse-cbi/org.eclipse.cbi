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
 * <a>href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">Eclipse winsigner webservice</a>.
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
     * Official eclipse signer service url as described in
     * http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F
     */
    private String signerUrl = "http://build.eclipse.org:31338/winsign.php";
    
    /**
     * @parameter expression="${project.build.directory}"
     */
    private File workdir;
    
    /**
     * @parameter expression="${project.artifactId}"
     */
    private String artifactID;
    
    /**
     * List of full executable paths.
     * If configured only these executables will be signed.
     * @parameter expression="${signFiles}"
     */
    private String[] signFiles;
    
    /**
     * Base dir to search for executables to sign.
     * If NOT configured baseSearchDir is ${project.build.directory}/products/${project.artifactId/}
     * @parameter expression="${baseSearchDir}
     */
    private String baseSearchDir;
    
    /**
     * List of file names to sign.
     * If NOT configured 'eclipse.exe' and 'eclipsec.exe' are signed. 
     * @parameter expression="${fileNames}
     */
    private String[] fileNames;

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
    		File searchDir = getSearchDir();
    		if (searchDir != null) { 
    			traverseDirectory(searchDir);
    		}
    	}

    }
    
    /**
     * @return Directory to start the search from or 
     * 		   null if the directory cannot be found. A file
     * 		   will not be returned.
     */
    private File getSearchDir() {
    	//base search dir is set
    	if (baseSearchDir != null && !baseSearchDir.isEmpty()) {
    		File dir = new File(baseSearchDir);
    		if(dir.isDirectory()) {
    			return dir;
    		}else {
    			getLog().error(dir + " is not a valid directory, failed to sign artifacts.");
    			return null;
    		}
    	} else { //deriving search path
    		if (artifactID == null || artifactID.isEmpty()) {
    			getLog().error("${project.artifactID} is not available, failed to sign artifacts.");
    			return null;
    		}
    		StringBuffer path = new StringBuffer(workdir.getPath());
    		path.append("/products/");
    		path.append(artifactID);
    		File dir = new File(path.toString());
    		
    		if(dir.isDirectory()) {
    			return dir;
    		}else {
    			getLog().error("Derived directory: " + dir + " is not a valid directory, failed to sign artifacts.");
    			return null;
    		}
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
                    throw new MojoExecutionException( "Could not sign artifact " + file );
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
            throw new MojoExecutionException( "Could not sign file " + file, e );
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
