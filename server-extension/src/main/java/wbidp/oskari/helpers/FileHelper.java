package wbidp.oskari.helpers;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileHelper {
    private static final Logger LOG = LogFactory.getLogger(FileHelper.class);

    /**
     * A constants for buffer size used to read/write data
     * */
    private static final int BUFFER_SIZE = 4096;

    /**
     * Processes and unzips a zip-archive to the defined path.
     *
     * @param pathToZip the path to the original zip-archive on disk
     * @param destDir the destination for the unzipped file(s)/folder(s)
     */
    public static void unzipArchive(String pathToZip, File destDir) {
        byte[] buffer = new byte[1024];
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(pathToZip));
            ZipEntry zipEntry = null;
            zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                zipEntry = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compresses a list of files to a destination zip file
     *
     * @param listFiles
     * @param destZipFile
     */
    public static void zip(File[] listFiles, String destZipFile) {
        try {
            ZipOutputStream zos = null;
            zos = new ZipOutputStream(new FileOutputStream(destZipFile));

            for (File file : listFiles) {
                if (file.isDirectory()) {
                    zipDirectory(file, file.getName(), zos);
                } else {
                    zipFile(file, zos);
                }
            }
            zos.flush();
            zos.close();
        } catch (Exception ex) {

        }
    }

    /**
     * Adds a directory to the current zip output stream
     *
     * @param folder the directory to be  added
     * @param parentFolder the path of parent directory
     * @param zos the current zip output stream
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static void zipDirectory(File folder, String parentFolder,
                              ZipOutputStream zos) throws FileNotFoundException, IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zos);
                continue;
            }
            zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            long bytesRead = 0;
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = bis.read(bytesIn)) != -1) {
                zos.write(bytesIn, 0, read);
                bytesRead += read;
            }
            zos.closeEntry();
        }
    }

    /**
     * Adds a file to the current zip output stream
     *
     * @param file the file to be added
     * @param zos the current zip output stream
     */
    private static void zipFile(File file, ZipOutputStream zos) {
        try {
            zos.putNextEntry(new ZipEntry(file.getName()));
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            long bytesRead = 0;
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = bis.read(bytesIn)) != -1) {
                zos.write(bytesIn, 0, read);
                bytesRead += read;
            }
            zos.closeEntry();
        } catch (Exception ex) {

        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        File destFolder = new File(destDirPath);
        if (!destFolder.exists()) {
            destFolder.mkdir();
        }

        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    /**
     * Iterates through zip content and returns the file name for the
     * given file extension. Returns null if a file is not found
     * with the given file extension
     *
     * @param fileExtension the file extension to look for
     * @return file name or null if a file is not found
     */
    public static String getFileNameFromZip(String zipFilePath, String fileExtension) {
        ZipFile zipFile = null;
        String fileName = null;
        try {
            zipFile = new ZipFile(zipFilePath);
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                String entryName = entry.getName();
                if (entryName.contains("." + fileExtension)) {
                    fileName = entryName.substring(0, entryName.indexOf("."));
                    break;
                }
            }
        } catch (IOException ioe) {
            LOG.error("Error opening zip file" + ioe);
        } finally {
            try {
                if (zipFile != null) {
                    zipFile.close();
                }
            } catch (IOException ioe) {
                LOG.error("Error while closing zip file" + ioe);
            }
        }
        return fileName;
    }

    public static File renameFilesInZip(String dataFilePath, File destFolder, String toBeReplaced, String replacement) {
        unzipArchive(dataFilePath, destFolder);
        renameFilesInFolder(destFolder, toBeReplaced, replacement);
        String newZipPath = dataFilePath.replace(".zip", "_new.zip");
        zip(destFolder.listFiles(), newZipPath);
        return new File(newZipPath);
    }

    public static void renameFilesInFolder(File folder, String toBeReplaced, String replacement) {
        File[] files = folder.listFiles();
        iterateFolder(files, toBeReplaced, replacement);
    }

    private static void iterateFolder(File[] files, String toBeReplaced, String replacement) {
        for (File file : files) {
            if (file.isDirectory()) {
                iterateFolder(file.listFiles(), toBeReplaced, replacement);
            } else {
                File destFile = new File(file.getAbsolutePath().replaceAll(toBeReplaced, replacement));

                if (file.renameTo(destFile)) {
                    LOG.info(String.format("File renamed successfully to %s", destFile.getName()));
                } else {
                    LOG.info(String.format("Failed to rename file %s", file.getName()));
                }
            }
        }
    }
}
