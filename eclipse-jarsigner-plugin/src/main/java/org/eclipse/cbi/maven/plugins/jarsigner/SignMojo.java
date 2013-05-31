/*******************************************************************************
 * Copyright (c) 2012, 2013 Eclipse Foundation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eclipse Foundation - initial API and implementation
 *   Thanh Ha (Eclipse Foundation) - Add support for signing inner jars
 *******************************************************************************/

package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.io.RawInputStreamFacade;

/**
 * Signs project main and attached artifact using <a
 * href="http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F">Eclipse jarsigner webservice</a>.
 * Only artifacts that have extension ``.jar'', other artifacts are not signed with a debug log message.
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
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * Official eclipse signer service url as described in
     * http://wiki.eclipse.org/IT_Infrastructure_Doc#Sign_my_plugins.2FZIP_files.3F
     *
     * @parameter default-value="http://build.eclipse.org:31338/sign" )
     * @required
     * @readonly
     */
    private String signerUrl;

    /**
     * @parameter expression="${project.build.directory}"
     */
    private File workdir;

    /**
     * Project types which this plugin supports.
     * 
     * @parameter
     */
    private List<String> supportedProjectTypes = Arrays.asList( "jar", // standard jars
                                                                "bundle", // felix/bnd bundles
                                                                // tycho
                                                                "eclipse-plugin", "eclipse-test-plugin",
                                                                "eclipse-feature" );

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !supportedProjectTypes.contains( project.getPackaging() ) )
        {
            getLog().debug( "Ignore unsupported project " + project );
            return;
        }

        signArtifact( project.getArtifact() );

        for ( Artifact artifact : project.getAttachedArtifacts() )
        {
            signArtifact( artifact );
        }
    }

    protected void signArtifact( Artifact artifact )
        throws MojoExecutionException
    {
        try
        {
            File file = artifact.getFile();
            if ( !file.isFile() || !file.canRead() )
            {
                getLog().warn( "Could not read artifact file, the artifact is not signed " + artifact );
                return;
            }

            if ( !"jar".equals( artifact.getArtifactHandler().getExtension() ) )
            {
                getLog().debug( "Artifact extention is not ``jar'', the artifact is not signed " + artifact );
                return;
            }

            if ( !shouldSign( file ) )
            {
                getLog().info( "Signing of " + artifact
                                   + " is disabled in META-INF/eclipse.inf, the artifact is not signed." );
                return;
            }

            boolean signInnerJars = shouldSignInnerJars( file );
            List<String> innerJars = new ArrayList<String>();
            if ( !signInnerJars ) {
                getLog().info( "Signing of inner jars for " + artifact
                        + " is disabled in META-INF/eclipse.inf, inner jars will not be signed.");
            } else {
                // Check if there are inner jars to sign
                getLog().info( "Searching " + file.getName() + " for inner jars..." );
                JarFile jar = new JarFile(file);
                Enumeration<JarEntry> jarEntries = jar.entries();
                while (jarEntries.hasMoreElements())
                {
                    JarEntry entry = jarEntries.nextElement();
                    if ( "jar".equals(FileUtils.getExtension(entry.getName() ) ) )
                    {
                        getLog().debug( "Inner jar found: " + entry.getName() );
                        innerJars.add(entry.getName());
                    }
                }
            }

            final long start = System.currentTimeMillis();

            workdir.mkdirs();

            // Sign inner jars if there are any
            if ( signInnerJars && innerJars.size() > 0) {
                signInnerJars(file, innerJars);
            }

            // Sign Artifact

            File tempSigned = File.createTempFile( file.getName(), ".signed-jar", workdir );
            try
            {
                signFile( file, tempSigned );
                if ( !tempSigned.canRead() || tempSigned.length() <= 0 )
                {
                    throw new MojoExecutionException( "Could not sign artifact " + artifact );
                }
                FileUtils.copyFile( tempSigned, file );
            }
            finally
            {
                tempSigned.delete();
            }

            getLog().info( "Signed " + artifact + " in " + ( ( System.currentTimeMillis() - start ) / 1000 )
                               + " seconds." );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not sign artifact " + artifact, e );
        }
    }

    private boolean shouldSign( File file )
        throws IOException
    {
        boolean sign = true;

        JarFile jar = new JarFile( file );
        try
        {
            ZipEntry entry = jar.getEntry( "META-INF/eclipse.inf" );
            if ( entry != null )
            {
                InputStream is = jar.getInputStream( entry );
                Properties eclipseInf = new Properties();
                try
                {
                    eclipseInf.load( is );
                }
                finally
                {
                    is.close();
                }

                sign =
                    !Boolean.parseBoolean( eclipseInf.getProperty( "jarprocessor.exclude" ) )
                        && !Boolean.parseBoolean( eclipseInf.getProperty( "jarprocessor.exclude.sign" ) );
            }
        }
        finally
        {
            jar.close();
        }

        return sign;
    }

    /**
     * Check if signing of inner jars should be disabled.
     * see: http://wiki.eclipse.org/JarProcessor_Options
     *
     * @param file jar file to check if inner jars should be signed
     */
    private boolean shouldSignInnerJars( File file )
        throws IOException
    {
        boolean sign = true;

        JarFile jar = new JarFile( file );
        try
        {
            ZipEntry entry = jar.getEntry( "META-INF/eclipse.inf" );
            if ( entry != null )
            {
                InputStream is = jar.getInputStream( entry );
                Properties eclipseInf = new Properties();
                try
                {
                    eclipseInf.load( is );
                }
                finally
                {
                    is.close();
                }

                sign =
                    !Boolean.parseBoolean( eclipseInf.getProperty( "jarprocessor.exclude.children" ) )
                        && !Boolean.parseBoolean( eclipseInf.getProperty( "jarprocessor.exclude.children.sign" ) );
            }
        }
        finally
        {
            jar.close();
        }

        return sign;
    }

    private void signFile( File source, File target )
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

    /**
     * Signs the inner jars in a jar file
     *
     * @param file jar file containing inner jars to be signed
     * @param innerJars A list of inner jars that needs to be signed
     */
    private void signInnerJars(File file, List<String> innerJars)
        throws IOException, FileNotFoundException, MojoExecutionException
    {
        JarFile jar = new JarFile(file);
        File nestedWorkdir = new File(workdir + File.separator + "sign-innner-jars");
        nestedWorkdir.mkdirs();

        Enumeration<JarEntry> extractFiles = jar.entries();
        while (extractFiles.hasMoreElements()) {

            JarEntry entry = extractFiles.nextElement();
            File f = new File(nestedWorkdir + File.separator + entry.getName());

            // Create directory if entry is a directory
            if (entry.isDirectory()) {
                f.mkdir();
                continue;
            }

            // Extract files from jar
            InputStream is = jar.getInputStream(entry);
            FileOutputStream fos = new FileOutputStream(f);
            while (is.available() > 0) {
                fos.write(is.read());
            }

            // cleanup
            fos.close();
            is.close();
        }

        // Sign inner jars
        for ( Iterator<String> it = innerJars.iterator(); it.hasNext(); )
        {
            final long start = System.currentTimeMillis();

            String jarToSign = it.next();
            File unsignedJar = new File( nestedWorkdir, jarToSign );
            File tempSignedJar = new File( nestedWorkdir, jarToSign + ".signed-jar" );

            signFile( unsignedJar, tempSignedJar );
            FileUtils.copyFile( tempSignedJar, unsignedJar );

            // cleanup
            tempSignedJar.delete();

            getLog().info( "Signed " + jarToSign + " in " + ( ( System.currentTimeMillis() - start ) / 1000 )
                    + " seconds." );
        }

        // repack jar
        File tempJar = File.createTempFile( file.getName(), ".repack-jar", workdir );
        try
        {
            getLog().debug( "Repacking " + file.getName() );
            repackJar(tempJar, nestedWorkdir);
            if ( !tempJar.canRead() || tempJar.length() <= 0 )
            {
                throw new MojoExecutionException( "Could not repack jar " + file.getName() );
            }
            FileUtils.copyFile( tempJar, file );
        }
        finally
        {
            tempJar.delete();
        }

        // cleanup
        FileUtils.deleteDirectory(nestedWorkdir);
    }

    /**
     * Creates a jar file from a directory
     *
     * @param jarFile filename of jar being created
     * @param jarDir directory to jar
     */
    private void repackJar( File jarFile, File jarDir )
        throws IOException, FileNotFoundException
    {
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile));

        for (File f : FileUtils.getFiles(jarDir, "**/*", "", false))
        {
            getLog().debug( "   " + f.getPath());

            if (f.isDirectory())
            {
                // Directories need to end with a forward slash
                JarEntry entry = new JarEntry(f.getPath().replace("\\", "/") + "/");
                entry.setTime(f.lastModified());
                jos.putNextEntry(entry);
                getLog().info("Directory: " + entry.getName());
            }
            else
            {
                JarEntry entry = new JarEntry(f.getPath().replace("\\", "/"));
                entry.setTime(f.lastModified());
                jos.putNextEntry(entry);

                // Write to file
                File writeFile = new File(jarDir, f.getPath());
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(writeFile));
                byte[] buffer = new byte[1024];
                while (true)
                {
                    int count = in.read(buffer);
                    if (count == -1) break;
                    jos.write(buffer, 0, count);
                }
                if (in != null) in.close();
            }
            jos.closeEntry();    // Don't forget to close the file entry
        }
        jos.close(); // Close the jar
    }
}
