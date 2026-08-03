package org.avni.server.importer.batch.csv;

import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.util.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.annotation.OnSkipInRead;
import org.springframework.batch.core.annotation.OnSkipInWrite;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;

import static java.lang.String.format;

@Component
@StepScope
public class ErrorFileWriterListener {

    private final BulkUploadS3Service bulkUploadS3Service;
    private static final Logger logger = LoggerFactory.getLogger(ErrorFileWriterListener.class);
    private final BugsnagReporter bugsnagReporter;
    @Value("#{jobParameters['uuid']}")
    private String uuid;
    private boolean readSkipReported;

    public ErrorFileWriterListener(BulkUploadS3Service bulkUploadS3Service, BugsnagReporter bugsnagReporter) {
        this.bulkUploadS3Service = bulkUploadS3Service;
        this.bugsnagReporter = bugsnagReporter;
    }

    @OnSkipInWrite
    public void onSkipInWrite(Row item, Throwable t) {
        appendToErrorFile(item, t);
    }

    @OnSkipInRead
    public void onSkipInRead(Throwable t) {
        String line = t instanceof FlatFileParseException ? ((FlatFileParseException) t).getInput() : "";
        String message = t.getCause() != null ? t.getCause().getMessage() : t.getMessage();
        try {
            // a read-side rejection is usually a whole-file condition (e.g. a cleared header hits every
            // row) - one report per step execution is enough; the listener is @StepScope
            if (!readSkipReported) {
                bugsnagReporter.logAndReportToBugsnag(t);
                readSkipReported = true;
            }
            FileWriter fileWriter = new FileWriter(bulkUploadS3Service.getLocalErrorFile(uuid), true);
            fileWriter.append(line);
            fileWriter.append(",\"");
            fileWriter.append(message);
            fileWriter.append("\"\n");
            fileWriter.close();
        } catch (IOException e) {
            logger.error("Error recording error", e);
            throw new RuntimeException(format("Error recording error: '%s'", e.getMessage()));
        }
    }

    public void appendToErrorFile(Row item, Throwable t) {
        try {
            bugsnagReporter.logAndReportToBugsnag(t);
            FileWriter fileWriter = new FileWriter(bulkUploadS3Service.getLocalErrorFile(uuid), true);
            fileWriter.append(item.toString());
            fileWriter.append(",\"");
            fileWriter.append(t.getMessage());
            fileWriter.append("\"\n");
            fileWriter.close();
        } catch (IOException e) {
            logger.error("Error recording error", e);
            throw new RuntimeException(format("Error recording error: '%s'", e.getMessage()));
        }
    }
}

