/*******************************************************************************
 * Copyright (c) 2007-2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     David Williams - initial API and implementation
 *     This file is a modified version of similar one, created by 
 *     the same author, in the WTP Project.
 *******************************************************************************/

package org.eclipse.cbi.releng.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

public class UpdatePackPropertiesFile extends Task {

    static class JarFileFilter implements FilenameFilter {

        public boolean accept(File dir, String name) {
            if (name.endsWith(".jar")) {
                return true;
            }
            return false;
        }

    }

    private static final String LINE_SEPARATOR_PROPERTY_NAME = "line.separator";
    // private static final String PATH_SEPARATOR_PROPERTY_NAME =
    // "path.separator";
    private static final String FILE_SEPARATOR_PROPERTY_NAME = "file.separator";

    private static String       EOL                          = System.getProperty(LINE_SEPARATOR_PROPERTY_NAME);
    // private static String PATH_SEPERATOR =
    // System.getProperty(PATH_SEPARATOR_PROPERTY_NAME);
    private static String       FILE_SEPERATOR               = System.getProperty(FILE_SEPARATOR_PROPERTY_NAME);

    private boolean             verbose;
    private String              archiveFilename;
    private FilenameFilter      jarFileFilter                = new JarFileFilter();
    private String              tempdir;

    /**
     * Purely a test method while in workbench
     * 
     * @param args
     */
    public static void main(String[] args) {
        UpdatePackPropertiesFile testInstance = new UpdatePackPropertiesFile();
        testInstance.setVerbose(true);
        String archiveName = "/home/davidw/temp/fromSigningDir/site_1676022728.zip";
        testInstance.setArchiveFilename(archiveName);
        testInstance.execute();
    }

    public void execute() throws BuildException {

        boolean invalidProperties = false;
        if (getArchiveFilename() == null) {
            log("archiveFilename must be set");
            invalidProperties = true;
        }
        ZipFile archiveFile = null;
        try {
            archiveFile = new ZipFile(getArchiveFilename());
            archiveFile.close();
        }
        catch (IOException e) {
            invalidProperties = true;
            log(e.getLocalizedMessage());
        }
        if (invalidProperties) {
            throw new BuildException("The properties for this task are not valid. See log for more details");
        }
        if (isVerbose()) {
            log("verbose logging is enabled");
        }
        try {
            excludeSignedJars();
        }
        catch (IOException e) {
            throw new BuildException(e);
        }

    }

    private void excludeSignedJars() throws IOException {
        String result = "";
        String destinationdirectory = null;
        String zipfilename = getArchiveFilename();
        String zipfilenameOnly = nameOnly(zipfilename);
        // we add zip file name as part of directory name, to be sure
        // it is unique (since more than one build can be running).
        destinationdirectory = getTempdir() + zipfilenameOnly + FILE_SEPERATOR;

        log("Finding jars already signed");
        log("destinationdirectory: " + destinationdirectory, Project.MSG_DEBUG);

        File tempDestDir = ensureNewDirectory(destinationdirectory);

        extractZipStream(destinationdirectory, zipfilename);

        // we avoid some recursing since we know structure, and know jars we
        // want to examine. Note, made to work with either traditional zips,
        // or the more modern repositories, though only one form would apply per run.

        // this first form is for traditional zips, which have 'eclipse' at top level
        File featureDestDir = new File(destinationdirectory + "eclipse/features/");
        String[] featurejars = featureDestDir.list(jarFileFilter);
        File bundlesDestDir = new File(destinationdirectory + "eclipse/plugins/");
        String[] bundlejars = bundlesDestDir.list(jarFileFilter);

        result = checkIfJarsSigned(result, destinationdirectory, "eclipse/features/", featurejars);
        result = checkIfJarsSigned(result, destinationdirectory, "eclipse/plugins/", bundlejars);

        // this form is for 'repository' form, which have features and plugins at top.
        File featureDestDirrepo = new File(destinationdirectory + "features/");
        String[] featurejarsrepo = featureDestDirrepo.list(jarFileFilter);
        File bundlesDestDirrepo = new File(destinationdirectory + "plugins/");
        String[] bundlejarsrepo = bundlesDestDirrepo.list(jarFileFilter);

        result = checkIfJarsSigned(result, destinationdirectory, "features/", featurejarsrepo);
        result = checkIfJarsSigned(result, destinationdirectory, "plugins/", bundlejarsrepo);

        if ((result != null) && (result.length() > 0) && result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }
        log("list of jars already signed: " + result);

        log("adding pack.properties to archive");
        FileWriter packFile = new FileWriter(destinationdirectory + "pack.properties");
        packFile.write("pack200.default.args=-E4" + EOL);
        packFile.write("pack.excludes=" + result + EOL);
        packFile.write("sign.excludes=" + result + EOL);
        packFile.close();

        // again, include zip file name to make sure unique
        String tempzipFileName = getTempdir() + "tempzip" + zipfilenameOnly + ".zip";
        File tempnewarchive = new File(tempzipFileName);
        // delete, if exists from previous run
        if (tempnewarchive.exists()) {
            tempnewarchive.delete();
        }
        zipDirectory(destinationdirectory, tempzipFileName);

        File originalarchive = new File(getArchiveFilename());
        // we mostly back this up in case we need to compare original with
        // tweaked version. Eventually, when confidence gained, this could be
        // removed.
        File tempbackup = new File(getArchiveFilename() + ".bak");
        if (tempbackup.exists()) {
            tempbackup.delete();
        }
        boolean success = originalarchive.renameTo(tempbackup);
        if (!success) {
            throw new BuildException("Could not rename original zip file to backup name.");
        }

        File newarchive = new File(tempzipFileName);
        // For some reason, move (rename) did not work on Linux?
        copyFile(newarchive, originalarchive);

        log("Updated archive file " + originalarchive.getName());
        // now lets clean up
        if (tempDestDir.exists()) {
            deleteDirectory(tempDestDir);
        }

    }

    private static void copyFile(File source, File dest) throws IOException {
        FileChannel sourceChannel = null;
        FileChannel destChannel = null;
        FileInputStream sourceStream = null;
        FileOutputStream destStream = null;
        try {
            sourceStream = new FileInputStream(source);
            destStream = new FileOutputStream(dest);
            sourceChannel = sourceStream.getChannel();
            destChannel = destStream.getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
        finally {
            if (sourceStream != null) {
                sourceStream.close();
            }
            if (destStream != null) {
                destStream.close();
            }
            if (sourceChannel != null) {
                sourceChannel.close();
            }
            if (destChannel != null) {
                destChannel.close();
            }
        }
    }

    private File ensureNewDirectory(String destinationdirectory) {
        // delete if exists, to remove items from previous failed runs
        File tempDestDir = new File(destinationdirectory);
        if (tempDestDir.exists()) {
            deleteDirectory(tempDestDir);
        }

        boolean createDir = tempDestDir.mkdirs();
        if (!createDir) {
            throw new BuildException("Could not create temporary working directory: " + tempDestDir);
        }
        return tempDestDir;
    }

    private void deleteDirectory(File tempDestDir) {

        File[] files = tempDestDir.listFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                // call recursively
                deleteDirectory(file);
            } else {
                boolean filedeleted = file.delete();
                if (!filedeleted) {
                    if (isVerbose()) {
                        log("could not delete temporary file: " + file);
                    }
                }
            }
        }
        boolean success = tempDestDir.delete();
        if (!success) {
            log("could not delete temporary desination directory: " + tempDestDir);
            log("   so requested 'deleteOnExit' for that temporary directory");
            tempDestDir.deleteOnExit();
        } else {
            if (isVerbose()) {
                log("successfully removed temporary desination directory: " + tempDestDir);
            }
        }

    }

    private String nameOnly(String zipfilename) {
        String result = zipfilename;

        // note, not sure if we should check for both '/' and '\\'?
        int lastSeperatorPos = zipfilename.lastIndexOf(FILE_SEPERATOR);
        // we use -4 for length of extension: ".zip"
        result = result.substring(lastSeperatorPos + 1, result.length() - 4);

        log("filenameonly: " + result);

        return result;
    }

    private void zipDirectory(String dir, String zipFileName) {
        File dirObj = new File(dir);

        try {

            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName));
            log("Creating : " + zipFileName, Project.MSG_DEBUG);
            addDir(dirObj, out, dir);
            // Complete and close the ZIP file
            out.close();

        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void addDir(File dirObj, ZipOutputStream out, String rootDirectory) throws IOException {
        File[] files = dirObj.listFiles();
        byte[] tmpBuf = new byte[1024];

        for (int i = 0; i < files.length; i++) {
            String entryName = files[i].getAbsolutePath().substring(rootDirectory.length());
            if (!entryName.equals(".") && !entryName.equals("..")) {
                entryName = slashify(entryName, files[i].isDirectory());
                if (files[i].isDirectory()) {
                    log(" Adding Directory Entry: " + entryName, Project.MSG_DEBUG);
                    out.putNextEntry(new ZipEntry(entryName));
                    // now recurse through the directory
                    addDir(files[i], out, rootDirectory);
                } else {
                    FileInputStream in = new FileInputStream(files[i].getAbsolutePath());
                    log(" Adding: " + entryName, Project.MSG_DEBUG);

                    out.putNextEntry(new ZipEntry(entryName));

                    // Transfer from the file to the ZIP file
                    int len;
                    while ((len = in.read(tmpBuf)) > 0) {
                        out.write(tmpBuf, 0, len);
                    }

                    // Complete the entry
                    out.closeEntry();
                    in.close();
                }
            }
        }
    }

    private String checkIfJarsSigned(String currentresults, String destinationDir, String parentDir, String[] jars)
            throws IOException {
        if (jars != null) {
            for (int i = 0; i < jars.length; i++) {
                String jarname = jars[i];
                JarFile jarFile = new JarFile(destinationDir + parentDir + jarname);
                Enumeration jarentries = jarFile.entries();
                while (jarentries.hasMoreElements()) {
                    JarEntry jarentry = (JarEntry) jarentries.nextElement();
                    String entryname = jarentry.getName();
                    log(entryname, Project.MSG_DEBUG);
                    if (entryname.endsWith("ECLIPSE.SF") || entryname.endsWith("ECLIPSEF.SF") || entryname.endsWith("ECLIPSE_.SF")
                            || entryname.endsWith("ECLIPSEF_.SF")) {
                        // log(parentDir + jarname);
                        currentresults = currentresults + (parentDir + jarname) + ",";
                        break;
                    }
                }
            }
        }
        return currentresults;
    }

    private void extractZipStream(String destinationdirectory, String zipfilename) throws IOException {

        // note we assume destinationdirectory already exists, ensured by caller.

        byte[] buf = new byte[1024];
        ZipInputStream zipinputstream = null;
        ZipEntry zipentry;

        File testFile = new File(zipfilename);
        if (!testFile.exists()) {
            log("Zip file, " + zipfilename + ", does not exist.");
        } else {
            zipinputstream = new ZipInputStream(new FileInputStream(zipfilename));

            // note we assume destinationdirectory already exists, ensured by caller. ?
            // ? but ... make sure destination exists
            File dir = new File(destinationdirectory);
            if (!dir.exists()) {
                boolean success = dir.mkdirs();
                if (!success) {
                    {
                        if (zipinputstream != null) {
                            zipinputstream.close();
                        }
                        throw new BuildException("Could not create directory: " + destinationdirectory);
                    }
                }
            }

            zipentry = zipinputstream.getNextEntry();
            while (zipentry != null) {
                // for each entry to be extracted
                String entryName = zipentry.getName();
                log("entryname: " + entryName, Project.MSG_DEBUG);
                int n;
                FileOutputStream fileoutputstream;

                String fullname = destinationdirectory + entryName;
                if (zipentry.isDirectory()) {
                    // we assume folder entries come before their files.
                    // not sure that's always true? Or if matters?
                    File newDir = new File(fullname);
                    newDir.mkdirs();
                } else {
                    File newFile = new File(fullname);

                    fileoutputstream = new FileOutputStream(newFile);

                    while ((n = zipinputstream.read(buf, 0, 1024)) > -1) {
                        fileoutputstream.write(buf, 0, n);
                    }
                    fileoutputstream.close();
                }

                zipinputstream.closeEntry();
                zipentry = zipinputstream.getNextEntry();

            } // while

            zipinputstream.close();

        }

    }

    public String getArchiveFilename() {
        return archiveFilename;
    }

    public void setArchiveFilename(String archiveFilename) {
        this.archiveFilename = archiveFilename;
    }

    private String slashify(String path, boolean isDirectory) {
        String p = path;
        if (File.separatorChar != '/') {
            p = p.replace(File.separatorChar, '/');
        }

        if (!p.endsWith("/") && isDirectory) {
            p = p + "/";
        }
        return p;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * If verbose is set, always log, no matter what level is set.
     */
    @Override
    public void log(String msg, int msgLevel) {
        if (isVerbose()) {
            super.log(msg, Project.MSG_INFO);
        } else {
            super.log(msg, msgLevel);
        }
    }

    private String getTempdir() {
        if (tempdir == null) {

            tempdir = System.getProperty("java.io.tmpdir");
            if (!(tempdir.endsWith("/") || tempdir.endsWith("\\"))) {
                tempdir = tempdir + FILE_SEPERATOR;
            }
            tempdir = tempdir + "tempupdatepackproperties" + FILE_SEPERATOR;
        }

        return tempdir;
    }
}
