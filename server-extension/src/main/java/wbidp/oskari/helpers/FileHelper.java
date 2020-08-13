package wbidp.oskari.helpers;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class FileHelper {
    private static final Logger LOG = LogFactory.getLogger(FileHelper.class);

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

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
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
}
