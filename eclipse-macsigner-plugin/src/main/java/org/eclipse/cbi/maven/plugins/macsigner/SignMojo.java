/*******************************************************************************
 * Copyright (c) 2013 Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Caroline McQuatt, Mike Lim - initial implementation
 *******************************************************************************/

package org.eclipse.cbi.maven.plugins.macsigner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

/**
 * Signs project main and attached artifact using
 * <a href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">Eclipse macsigner webservice</a>.
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
     * The signing service URL for signing Mac binaries
     *
     * <p>The signing service should return a signed zip file. Containing
     * the Mac *.app directory.</p>
     *
     * <p>The Official Eclipse signer service URL as described in the
     * <a href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">
     * wiki</a>.</p>
     *
     * <p><b>Configuration via Maven commandline</b></p>
     * <pre>-Dcbi.macsigner.signerUrl=http://localhost/macsign.php</pre>
     *
     * <p><b>Configuration via pom.xml</b></p>
     * <pre>{@code
     * <configuration>
     *   <signerUrl>http://localhost/macsign</signerUrl>
     * </configuration>
     * }</pre>
     *
     * @parameter expression="${cbi.macsigner.signerUrl}" default-value="http://build.eclipse.org:31338/macsign.php"
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
     * A list of full paths to the Mac directory *.app
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
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/macosx/cocoa/x86/eclipse/Eclipse.app{@code</signFile>
     *     <signFile>}${project.build.directory}/products/org.eclipse.sdk.ide/macosx/cocoa/x86_64/eclipse/Eclipse.app{@code</signFile>
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
     * A list of *.app filenames to sign
     *
     * <p>If NOT configured 'Eclipse.app' is signed.</p>
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

    /**
     * List of executable files on the .app file to be signed.
     */
    private static ArrayList<String> executableFiles = new ArrayList<String>();

    /**
     * Part of the unsigned zip file name.
     */
    private static final String UNSIGNED_ZIP_FILE_NAME = "app_unsigned";

    /**
     * Part of the signed zip file name.
     */
    private static final String SIGNED_ZIP_FILE_NAME = "app_signed";

    /**
     * The zip file extension.
     */
    private static final String ZIP_EXT = ".zip";

    /**
     * The number of byte written to the output stream during zip and unzip.
     */
    private static final int BUFFER_SIZE = 1024;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        //app paths are configured
        if (signFiles != null && !(signFiles.length == 0)) {
            for (String path : signFiles) {
                signArtifact(new File(path));
            }
        }
        else { //perform search
            if (fileNames == null || fileNames.length == 0) {
                fileNames = new String[1];
                fileNames[0] = "Eclipse.app";
            }

            File searchDir = new File(baseSearchDir);
            getLog().debug("Searching: " + searchDir);
            traverseDirectory(searchDir);
        }
    }

    /**
     * Recursive method. Searches the base directory for files to sign.
     * @param dir
     * @throws MojoExecutionException
     */
    private void traverseDirectory(File dir) throws MojoExecutionException {
        if (dir.isDirectory()) {
            getLog().debug("searching " + dir.getAbsolutePath());
            for(File file : dir.listFiles()){
                if (file.isFile()){
                    continue;
                } else if (file.isDirectory()) {
                    boolean isSigned = false;
                    String fileName = file.getName();
                    for(String allowedName : fileNames) {
                        if (fileName.equals(allowedName)) {
                            signArtifact(file); // signs the file
                            isSigned = true;
                            break;
                        }
                    }
                    if (!isSigned) // do not search directories that are already signed
                    {
                        traverseDirectory(file);
                    }
                }
            }
        }
        else {
            getLog().error("Internal error. " + dir + " is not a directory.");
        }
    }

    /**
     * Decompresses zip files.
     * @param zipFile           The zip file to decompress.
     * @throws IOException
     * @throws MojoExecutionException
     */
    private void unZip(File zipFile, File output_dir) throws IOException, MojoExecutionException
    {

        ZipArchiveInputStream zis = new ZipArchiveInputStream(new FileInputStream(zipFile));
        ZipArchiveEntry ze;
        String name, parent;
        try {
            ze = zis.getNextZipEntry();
            // check for at least one zip entry
            if (ze == null)
            {
                throw new MojoExecutionException( "Could not decompress " + zipFile);
            }

            while(ze != null) {
                name = ze.getName();

                //make directories
                if( ze.isDirectory())
                {
                    mkdirs(output_dir, name);
                }
                else
                {
                    parent = getParentDirAbsolutePath(name);
                    mkdirs(output_dir, parent);

                    File outFile = new File(output_dir, name);
                    outFile.createNewFile();

                    // check for match in executable list
                    if(executableFiles.contains(name))
                    {
                        Files.setPosixFilePermissions(outFile.toPath(), PosixFilePermissions.fromString("rwxr-x---"));
                    }

                    FileOutputStream fos = new FileOutputStream(outFile);

                    copyInputStreamToOutputStream(zis, fos);
                    fos.close();
                }
                ze = zis.getNextZipEntry();
            }
        } finally {
            zis.close();
        }
    }

    /**
     * Helper method to create a new file and make all of the necessary directories.
     * @param outdir            The parent of the new file.
     * @param path              The child path of the new file relative to the parent.
     */
    private static void mkdirs(File outdir, String path)
    {
        File d = new File(outdir, path);
        if( !d.exists() )
            d.mkdirs();
    }

    /**
     * Creates a zip file.
     * @param dir                   The Directory of the files to be zipped.
     * @param zip                   An output stream to write the file
     * @throws IOException
     */
    private void createZip(File dir, ZipArchiveOutputStream zip) throws IOException {
        Deque<File> dir_stack = new LinkedList<File>();
        dir_stack.push(dir);

        // base path is the parent of the "Application.app" folder
        // it will be used to make "Application.app" the top-level folder in the zip
        String base_path = getParentDirAbsolutePath(dir);

        // verify that "dir" actually id the ".app" folder
        if(!dir.getName().endsWith(".app"))
        	throw new IOException("Please verify the configuration. Directory does not end with '.app': " + dir);

        while (!dir_stack.isEmpty()) {

            File file = dir_stack.pop();
            File[] files = file.listFiles();

            for (File f : files) {
            	String name = f.getAbsolutePath().substring(base_path.length());
            	getLog().debug("Found: " + name);

                if (f.isFile() && isInContentsFolder(name))
                {
                	getLog().debug("Adding to zip file for signing: " + f);

                    ZipArchiveEntry entry = new ZipArchiveEntry(name);
                    zip.putArchiveEntry(entry);

                    if (f.canExecute())
                    {
                        //work around to track the relative file names
                        // of those that need to be set as executable on unZip
                        executableFiles.add(name);
                    }
                    InputStream is = new FileInputStream(f);
                    copyInputStreamToOutputStream(is, zip);

                    is.close();
                    zip.closeArchiveEntry();
                }
                else if (f.isDirectory() && isInContentsFolder(name))
                { //add directory entry
                    dir_stack.push(f);
                }
                else
                {
                    getLog().debug(f + " was not included in the zip file to be signed.");
                }
            }
        }
    }

	private boolean isInContentsFolder(String name) {
		String[] segments = name.split("/");
		return segments.length > 1 && segments[0].endsWith(".app") && segments[1].equals("Contents");
	}

    /**
     * Helper method. Returns the absolute path of a file's parent.
     * @param dir
     * @return          The absolute path of a file's parent.
     *                  Returns the empty string if there is no parent directory.
     */
    private String getParentDirAbsolutePath(File file)
    {
        return getParentDirAbsolutePath(file.getAbsolutePath());
    }

    /**
     * Helper method. Returns the absolute path of a file's parent.
     * @param name
     * @return          The absolute path of a file's parent.
     *                  Returns the empty string if there is no parent directory.
     */
    private String getParentDirAbsolutePath(String name)
    {
        int index = name.lastIndexOf(File.separator);
        return name.substring(0, index + 1);
    }

    /**
     * Helper method. Writes bytes from an InputStream to an OutputStream.
     * @param fis                   The InputStream.
     * @param zip                   The OutputStream.
     * @throws IOException
     */
    private void copyInputStreamToOutputStream(InputStream fis, OutputStream zip) throws IOException {
        byte[] buff = new byte[BUFFER_SIZE];

        while(true) {
            int r_count = fis.read(buff);
            if (r_count < 0) {
                break;
            }
            zip.write(buff, 0, r_count);
        }
    }

    /**
     * Signs the file.
     * @param file
     * @throws MojoExecutionException
     */
    protected void signArtifact( File file )
        throws MojoExecutionException
    {
        try
        {
            if (!file.isDirectory())
            {
                getLog().warn(file + " is a not a directory, the artifact is not signed.");
                return; // Expecting the .app directory
            }

            workdir.mkdirs();

            //zipping the directory
            getLog().debug("Building zip: " + file);
            File zipFile = File.createTempFile(UNSIGNED_ZIP_FILE_NAME, ZIP_EXT, workdir);
            ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new FileOutputStream(zipFile));

            createZip(file, zos);
            zos.finish();
            zos.close();

            final long start = System.currentTimeMillis();

            String base_path = getParentDirAbsolutePath(file);
            File zipDir = new File(base_path);
            File tempSigned = File.createTempFile(SIGNED_ZIP_FILE_NAME, ZIP_EXT, workdir );
            File tempSignedCopy = new File(base_path + File.separator + tempSigned.getName());

            if (tempSignedCopy.exists()) {
                String msg = "Could not copy signed file because a file with the same name already exists: " + tempSignedCopy;

                if (continueOnFail)
                {
                    getLog().warn(msg);
                }
                else
                {
                    throw new MojoExecutionException(msg);
                }
            }
            tempSignedCopy.createNewFile();

            FileUtils.copyFile(tempSigned, tempSignedCopy);

            try
            {
                postFile( zipFile, tempSigned );
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

                // unzipping response
                getLog().debug("Decompressing zip: " + file);
                unZip(tempSigned, zipDir);
            }
            finally
            {
                if (!zipFile.delete())
                {
                    getLog().warn("Temporary file failed to delete: " + zipFile);
                }
                if (!tempSigned.delete())
                {
                    getLog().warn("Temporary file failed to delete: " + tempSigned);
                }
                if (!tempSignedCopy.delete())
                {
                    getLog().warn("Temporary file failed to delete: " + tempSignedCopy);
                }
            }

            getLog().info( "Signed " + file + " in " + ( ( System.currentTimeMillis() - start ) / 1000 )
                               + " seconds." );
        }
        catch ( IOException e )
        {
            String msg = "Could not sign file " + file + ": " + e.getMessage();

            if (continueOnFail)
            {
                getLog().warn(msg);
            }
            else
            {
                throw new MojoExecutionException(msg, e);
            }
        }
        finally
        {
            executableFiles.clear();
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
    	getLog().debug("Sending file " + source + " to: " + signerUrl);

        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost( signerUrl );

        MultipartEntity reqEntity = new MultipartEntity();
        reqEntity.addPart( "file", new FileBody( source ) );
        post.setEntity( reqEntity );

        HttpResponse response = client.execute( post );
        getLog().debug("Signer replied: " + response.getStatusLine());

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
        	String responseMessage = getResponseAsText(resEntity);
        	if(responseMessage != null)
        	{
        		getLog().debug("Content received from signer:\n" + EntityUtils.toString(resEntity));
        	} else
        	{
        		getLog().debug("No content received from signer.");
        	}
            throw new MojoExecutionException( this, "Signer replied " + response.getStatusLine(), responseMessage );
        }
    }

	private String getResponseAsText(HttpEntity resEntity) {
		if(resEntity != null)
		{
			try
			{
				return EntityUtils.toString(resEntity);
			} catch (Exception e)
			{
				getLog().debug("Error reading response.");
				getLog().debug(e);
				return null;
			}
		}
		return null;
	}
}
