package org.avni.server.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MetadataDiffServiceTest {

    private MetadataDiffService metadataDiffService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        metadataDiffService = new MetadataDiffService();
    }

    @Test
    public void testCompareMetadataZips() throws IOException {
        MultipartFile zipFile1 = createMultipartFile("file1.json", "{\"key\":\"value1\"}");
        MultipartFile zipFile2 = createMultipartFile("file1.json", "{\"key\":\"value2\"}");

        assertNotNull(zipFile1);
        assertNotNull(zipFile2);

        Map<String, Object> differences = metadataDiffService.compareMetadataZips(zipFile1, zipFile2);

        assertNotNull(differences);
        assertEquals(1, differences.size());
    }

    private MultipartFile createMultipartFile(String fileName, String jsonContent) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zipOutputStream.putNextEntry(zipEntry);
            zipOutputStream.write(jsonContent.getBytes());
            zipOutputStream.closeEntry();
        }
        return new MockMultipartFile("file", "test.zip", "application/zip", byteArrayOutputStream.toByteArray());
    }

    @Test
    public void testFindDifferences() {
        Map<String, Object> jsonMap1 = new HashMap<>();
        Map<String, Object> jsonMap2 = new HashMap<>();

        jsonMap1.put("uuid1", createJsonObject("value1"));
        jsonMap2.put("uuid1", createJsonObject("value2"));
        jsonMap2.put("uuid2", createJsonObject("value3"));

        Map<String, Object> differences = metadataDiffService.findDifferences(jsonMap1, jsonMap2);

        assertNotNull(differences);

        assertTrue(differences.containsKey("uuid1"));
        assertTrue(differences.containsKey("uuid2"));
    }

    @Test
    public void testFindMissingFiles() {
        Set<String> fileNames1 = new HashSet<>();
        Set<String> fileNames2 = new HashSet<>();

        fileNames1.add("file1.json");
        fileNames1.add("file2.json");
        fileNames2.add("file1.json");

        Set<String> missingFiles = metadataDiffService.findMissingFiles(fileNames1, fileNames2);

        assertNotNull(missingFiles);

        assertTrue(missingFiles.contains("file2.json"));
        assertFalse(missingFiles.contains("file1.json"));
    }

    private Map<String, Object> createJsonObject(String value) {
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("key", value);
        return jsonObject;
    }
}
