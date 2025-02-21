package org.avni.server.service;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.domain.metadata.MetadataChangeReport;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.FileUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MetadataDiffServiceTest {
    private MetadataDiffService metadataDiffService;
    @Mock
    private BundleService bundleService;

    @Before
    public void setUp() {
        initMocks(this);
        MetadataBundleAndFileHandler bundleAndFileHandler = new MetadataBundleAndFileHandler();
        metadataDiffService = new MetadataDiffService(bundleAndFileHandler, bundleService);
        UserContextHolder.create(new UserContextBuilder().withOrganisation(new Organisation()).build());
    }

    @Test
    public void shouldFindDiff() throws IOException {
        MetadataChangeReport metadataChangeReport = compareJsons("{\"uuid\":\"a\", \"key\":\"value1\"}",
                "{\"uuid\":\"a\", \"key\":\"value2\"}");
        assertEquals(1, metadataChangeReport.getNumberOfModifications());
    }

    @Test
    public void sameFileShouldNtHaveChanges() throws IOException, URISyntaxException {
        assertTrue(hasNoChangesInSameFile("/metadataDiff/programs.json"));
        assertTrue(hasNoChangesInSameFile("/metadataDiff/reportDashboard.json"));
        assertTrue(hasNoChangesInSameFile("/metadataDiff/identifierSource.json"));
        assertTrue(hasNoChangesInSameFile("/metadataDiff/aForm.json"));
    }

    private boolean hasNoChangesInSameFile(String fileName) throws IOException, URISyntaxException {
        String json1 = FileUtil.readFileContentsFromClasspath(fileName);
        MetadataChangeReport changes = compareJsons(json1, json1);
        return changes.getNumberOfModifications() == 0;
    }

    private MetadataChangeReport compareJsons(String candidateJson, String json2) throws IOException {
        MultipartFile zipFile1 = createMultipartFile(candidateJson);
        currentJson(json2);
        return metadataDiffService.findChangesInBundle(zipFile1);
    }

    private void currentJson(String jsonContent) throws IOException {
        when(bundleService.createBundle(any(), anyBoolean())).thenReturn(createBundleOutputStream("file1.json", jsonContent));
    }

    private MultipartFile createMultipartFile(String jsonContent) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = createBundleOutputStream("file1.json", jsonContent);
        return new MockMultipartFile("file", "test.zip", "application/zip", byteArrayOutputStream.toByteArray());
    }

    private static ByteArrayOutputStream createBundleOutputStream(String fileName, String jsonContent) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zipOutputStream.putNextEntry(zipEntry);
            zipOutputStream.write(jsonContent.getBytes());
            zipOutputStream.closeEntry();
        }
        return byteArrayOutputStream;
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
}
