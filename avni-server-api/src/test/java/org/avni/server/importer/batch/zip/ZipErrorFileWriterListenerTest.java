package org.avni.server.importer.batch.zip;

import org.avni.server.importer.batch.model.BundleFile;
import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.util.FileUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ZipErrorFileWriterListenerTest {
    private ZipErrorFileWriterListener listener;
    private File errorFile;

    @Before
    public void setUp() throws Exception {
        errorFile = File.createTempFile("zipErrorFileWriterListenerTest", ".csv");
        errorFile.delete();
        errorFile.deleteOnExit();
        BulkUploadS3Service bulkUploadS3Service = mock(BulkUploadS3Service.class);
        when(bulkUploadS3Service.getLocalErrorFile("job-uuid")).thenReturn(errorFile);
        listener = new ZipErrorFileWriterListener(bulkUploadS3Service);
        ReflectionTestUtils.setField(listener, "uuid", "job-uuid");
    }

    @Test
    public void theFirstFailureLeadsTheFileWithTheMarkerExcelNeedsToReadIt() throws Exception {
        listener.writeError(bundleFile("forms/form.json"), new RuntimeException("ಶಾಲೆ not found"));

        String contents = new String(Files.readAllBytes(errorFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(contents.startsWith(FileUtil.UTF8_BOM));
        assertTrue(contents.contains("ಶಾಲೆ not found"));
    }

    @Test
    public void aLaterFailureDoesNotPutASecondMarkerInTheMiddleOfTheFile() throws Exception {
        listener.writeError(bundleFile("forms/one.json"), new RuntimeException("first"));
        listener.writeError(bundleFile("forms/two.json"), new RuntimeException("second"));

        String contents = new String(Files.readAllBytes(errorFile.toPath()), StandardCharsets.UTF_8);
        assertEquals(1, contents.split(FileUtil.UTF8_BOM, -1).length - 1);
    }

    @Test
    public void aQuoteInTheFailureMessageIsEscaped() throws Exception {
        listener.writeError(bundleFile("forms/form.json"), new RuntimeException("bad \"name\" here"));

        String contents = new String(Files.readAllBytes(errorFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(contents.contains("bad \"\"name\"\" here"));
    }

    private BundleFile bundleFile(String name) {
        return new BundleFile(name, new byte[0]);
    }
}
