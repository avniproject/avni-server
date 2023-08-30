package org.avni.server.importer.batch.csv;

import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.BulkUploadS3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.annotation.OnSkipInWrite;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

@Component
@StepScope
public class ErrorFileWriterListener {

    private final BulkUploadS3Service bulkUploadS3Service;
    private static final Logger logger = LoggerFactory.getLogger(ErrorFileWriterListener.class);
    @Value("#{jobParameters['uuid']}")
    private String uuid;

    public ErrorFileWriterListener(BulkUploadS3Service bulkUploadS3Service) {
        this.bulkUploadS3Service = bulkUploadS3Service;
    }

    @OnSkipInWrite
    public void onSkipInWrite(Row item, Throwable t) {
        appendToErrorFile(item, t);
    }

    public void appendToErrorFile(Row item, Throwable t) {
        try {
            String stackTrace = Stream.of(t.getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            FileWriter fileWriter = new FileWriter(bulkUploadS3Service.getLocalErrorFile(uuid), true);
            fileWriter.append(item.toString());
            fileWriter.append(",\"");
            fileWriter.append(t.getMessage());
            fileWriter.append("\n");
            fileWriter.append(t.getMessage() == null ? stackTrace : "");
            fileWriter.append("\"\n");
            fileWriter.close();
        } catch (IOException e) {
            logger.error("Error recording error", e);
            throw new RuntimeException(format("Error recording error: '%s'", e.getMessage()));
        }
    }
}

