package org.avni.server.importer.batch.csv;

import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.util.BugsnagReporter;
import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class ErrorFileWriterListenerTest {
    private ErrorFileWriterListener listener;
    private BugsnagReporter bugsnagReporter;
    private File errorFile;

    @Before
    public void setUp() throws Exception {
        errorFile = File.createTempFile("errorFileWriterListenerTest", ".csv");
        errorFile.deleteOnExit();
        BulkUploadS3Service bulkUploadS3Service = mock(BulkUploadS3Service.class);
        when(bulkUploadS3Service.getLocalErrorFile("job-uuid")).thenReturn(errorFile);
        bugsnagReporter = mock(BugsnagReporter.class);
        listener = new ErrorFileWriterListener(bulkUploadS3Service, bugsnagReporter);
        ReflectionTestUtils.setField(listener, "uuid", "job-uuid");
    }

    @Test
    public void readSkipWritesTheRawLineAndUnwrappedCauseToTheErrorFile() throws Exception {
        FlatFileParseException parseException = new FlatFileParseException("Parsing error at line: 3",
                new RuntimeException("Column(s) 2 have values but no header"), "v1,v2,v3", 3);

        listener.onSkipInRead(parseException);

        List<String> lines = Files.readAllLines(errorFile.toPath());
        assertEquals(1, lines.size());
        assertEquals("v1,v2,v3,\"Column(s) 2 have values but no header\"", lines.get(0));
    }

    @Test
    public void readSkipsReportToBugsnagOncePerExecution() {
        FlatFileParseException parseException = new FlatFileParseException("Parsing error",
                new RuntimeException("Column(s) 2 have values but no header"), "v1,v2", 3);

        listener.onSkipInRead(parseException);
        listener.onSkipInRead(parseException);

        verify(bugsnagReporter, times(1)).logAndReportToBugsnag(any());
    }
}
