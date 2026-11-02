package org.avni.server.service;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MetadataBundleAndFileHandlerTest {
    private final MetadataBundleAndFileHandler handler = new MetadataBundleAndFileHandler();

    private byte[] zipWith(String... entryNames) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String name : entryNames) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write("{}".getBytes());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    public void extractsNormalEntriesIntoTheTempDir() throws IOException {
        File dir = handler.extractZip(new ByteArrayInputStream(zipWith("forms/good.json", "concepts.json")));
        assertTrue(new File(dir, "forms/good.json").exists());
        assertTrue(new File(dir, "concepts.json").exists());
    }

    @Test
    public void rejectsAPathTraversalEntryAndWritesNothingOutsideTheTempDir() throws IOException {
        File marker = new File(System.getProperty("java.io.tmpdir"), "zipslip_test_marker.txt");
        //noinspection ResultOfMethodCallIgnored
        marker.delete();
        try {
            handler.extractZip(new ByteArrayInputStream(zipWith("../zipslip_test_marker.txt")));
            fail("extraction should reject an entry whose path escapes the temp directory");
        } catch (IOException e) {
            assertTrue("message should name a bad zip entry, was: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().toLowerCase().contains("bad zip entry"));
        } finally {
            boolean escaped = marker.exists();
            //noinspection ResultOfMethodCallIgnored
            marker.delete();
            assertFalse("the entry escaped the temp directory", escaped);
        }
    }
}
