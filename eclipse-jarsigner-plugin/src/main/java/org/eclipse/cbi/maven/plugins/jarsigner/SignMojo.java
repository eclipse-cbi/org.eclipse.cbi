package org.eclipse.cbi.maven.plugins.jarsigner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
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
     */
    private String signerUrl = "http://build.eclipse.org:31338/sign";

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

            final long start = System.currentTimeMillis();

            workdir.mkdirs();
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
}
