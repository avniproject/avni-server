package org.avni.server.importer.batch.csv;

import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.util.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.annotation.OnSkipInWrite;
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
    private BugsnagReporter bugsnagReporter;
    @Value("#{jobParameters['uuid']}")
    private String uuid;

    public ErrorFileWriterListener(BulkUploadS3Service bulkUploadS3Service, BugsnagReporter bugsnagReporter) {
        this.bulkUploadS3Service = bulkUploadS3Service;
        this.bugsnagReporter = bugsnagReporter;
    }

    @OnSkipInWrite
    public void onSkipInWrite(Row item, Throwable t) {
        appendToErrorFile(item, t);
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

