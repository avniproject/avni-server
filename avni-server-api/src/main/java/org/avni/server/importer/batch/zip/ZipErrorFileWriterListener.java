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

import org.avni.server.util.CsvCell;
import org.avni.server.util.FileUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
            File errorFile = bulkUploadS3Service.getLocalErrorFile(uuid);
            // This file has no header row to lead with the marker, and nothing else writes to it,
            // so the marker goes ahead of whichever failure happens to be recorded first.
            boolean startingAFreshFile = !errorFile.exists() || errorFile.length() == 0;
            FileWriter fileWriter = new FileWriter(errorFile, StandardCharsets.UTF_8, true);
            if (startingAFreshFile) fileWriter.append(FileUtil.UTF8_BOM);
            fileWriter.append(CsvCell.quoteIfNeeded(bundleFile.getName(), false));
            fileWriter.append(",");
            fileWriter.append(CsvCell.quoted((t.getMessage() == null ? "" : t.getMessage()) + "\n" + stackTrace));
            fileWriter.append("\n");
            fileWriter.close();
        } catch (IOException e) {
            logger.error("Error recording error", e);
            throw new RuntimeException(format("Error recording error: '%s'", e.getMessage()));
        }
    }
}

