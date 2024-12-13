package org.avni.server.importer.batch.zip;

import org.avni.server.importer.batch.model.BundleFile;
import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.annotation.OnProcessError;
import org.springframework.batch.core.annotation.OnReadError;
import org.springframework.batch.core.annotation.OnSkipInWrite;
import org.springframework.batch.core.annotation.OnWriteError;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;

import static java.lang.String.format;

@Component
@StepScope
public class ZipErrorFileWriterListener {
    private final BulkUploadS3Service bulkUploadS3Service;
    private static final Logger logger = LoggerFactory.getLogger(ZipErrorFileWriterListener.class);

    @Value("#{jobParameters['uuid']}")
    private String uuid;

    public ZipErrorFileWriterListener(BulkUploadS3Service bulkUploadS3Service) {
        this.bulkUploadS3Service = bulkUploadS3Service;
    }

    @OnSkipInWrite
    public void onSkipInWrite(BundleFile bundleFile, Throwable throwable) {
        logger.error("onSkipInWrite", throwable);
        writeError(bundleFile, throwable);
    }

    @OnProcessError
    public void onProcessError(BundleFile bundleFile, Exception e) {
        logger.error("onProcessError", e);
        writeError(bundleFile, e);
    }

    @OnReadError
    public void onReadError(Exception e) {
        logger.error("onReadError", e);
    }

    @OnWriteError
    public void onWriteError(Exception e, Chunk o) {
        logger.error("onWriteError", e);
    }

    public void writeError(BundleFile bundleFile, Throwable t) {
        try {
            String stackTrace = ExceptionUtil.getFullStackTrace(t);
            FileWriter fileWriter = new FileWriter(bulkUploadS3Service.getLocalErrorFile(uuid), true);
            fileWriter.append(bundleFile.getName());
            fileWriter.append(",\"");
            fileWriter.append(t.getMessage() == null ? "" : t.getMessage().replaceAll("\"", "\"\""));
            fileWriter.append("\n");
            fileWriter.append(stackTrace);
            fileWriter.append("\"\n");
            fileWriter.close();
        } catch (IOException e) {
            logger.error("Error recording error", e);
            throw new RuntimeException(format("Error recording error: '%s'", e.getMessage()));
        }
    }
}

