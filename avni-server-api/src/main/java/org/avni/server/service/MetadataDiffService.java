package org.avni.server.service;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.metadata.MetadataChangeReport;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class MetadataDiffService {
    private static final Logger logger = LoggerFactory.getLogger(MetadataDiffService.class);

    private final MetadataBundleAndFileHandler bundleAndFileHandler;
    private final MetadataDiffChecker diffChecker;
    private final BundleService bundleService;

    @Autowired
    public MetadataDiffService(MetadataBundleAndFileHandler bundleAndFileHandler, MetadataDiffChecker diffChecker, BundleService bundleService) {
        this.bundleAndFileHandler = bundleAndFileHandler;
        this.diffChecker = diffChecker;
        this.bundleService = bundleService;
    }

    public MetadataChangeReport findChangesInBundle(MultipartFile incumbentBundleFile) {
        MetadataChangeReport metadataChangeReport = new MetadataChangeReport();
        File incumbmentDir = null, existingConfigBundleDir = null;

        try {
            Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
            incumbmentDir = bundleAndFileHandler.extractZip(incumbentBundleFile);
            ByteArrayOutputStream bundleOutputStream = this.bundleService.createBundle(organisation, false);
            ByteArrayInputStream stream = new ByteArrayInputStream(bundleOutputStream.toByteArray());
            existingConfigBundleDir = bundleAndFileHandler.extractZip(stream);

            List<File> incumbentFiles = bundleAndFileHandler.listJsonFiles(incumbmentDir);
            List<File> existingConfigBundleFiles = bundleAndFileHandler.listJsonFiles(existingConfigBundleDir);

            Map<String, Map<String, Object>> incumbentJsonFileValues = bundleAndFileHandler.parseJsonFiles(incumbentFiles, incumbmentDir);
            Map<String, Map<String, Object>> existingConfigJsonFileValues = bundleAndFileHandler.parseJsonFiles(existingConfigBundleFiles, existingConfigBundleDir);

            Set<String> incumbentJsonFiles = incumbentJsonFileValues.keySet();
            Set<String> existingConfigJsonFiles = existingConfigJsonFileValues.keySet();

            Set<String> commonFileNames = new HashSet<>(incumbentJsonFiles);
            commonFileNames.retainAll(existingConfigJsonFiles);

            for (String fileName : commonFileNames) {
                Map<String, Object> incumbentJsonValues = incumbentJsonFileValues.get(fileName);
                Map<String, Object> existingConfigJsonValues = existingConfigJsonFileValues.get(fileName);

                if (incumbentJsonValues != null && existingConfigJsonValues != null) {
                    Map<String, Object> fileDifferences = diffChecker.findDifferences(incumbentJsonValues, existingConfigJsonValues);
                    if (!fileDifferences.isEmpty()) {
                        metadataChangeReport.put(fileName, fileDifferences);
                    }
                }
            }

            Set<String> filesMissingInIncumbent = findMissingFiles(incumbentJsonFiles, existingConfigJsonFiles);
            if (!filesMissingInIncumbent.isEmpty()) {
                metadataChangeReport.putAll(missingFilesMap(filesMissingInIncumbent, "Missing Files in UAT ZIP"));
            }

            Set<String> filesMissingInExisting = findMissingFiles(existingConfigJsonFiles, incumbentJsonFiles);
            if (!filesMissingInExisting.isEmpty()) {
                metadataChangeReport.putAll(missingFilesMap(filesMissingInExisting, "Missing Files in PROD ZIP"));
            }
        } catch (IOException e) {
            logger.error("Error comparing metadata ZIPs: " + e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Error comparing metadata ZIPs: " + e.getMessage());
            metadataChangeReport.put("error", errorResult);
        } finally {
            if (incumbmentDir != null) {
                deleteDirectory(incumbmentDir);
            }
            if (existingConfigBundleDir != null) {
                deleteDirectory(existingConfigBundleDir);
            }
        }
        return metadataChangeReport;
    }

    protected Set<String> findMissingFiles(Set<String> incumbentJsonFiles, Set<String> existingConfigJsonFiles) {
        Set<String> missingInIncumbentFiles = new HashSet<>(incumbentJsonFiles);
        missingInIncumbentFiles.removeAll(existingConfigJsonFiles);
        return missingInIncumbentFiles;
    }

    protected Map<String, Object> missingFilesMap(Set<String> missingFiles, String message) {
        Map<String, Object> missingFilesMap = new LinkedHashMap<>();
        missingFilesMap.put(message, missingFiles);
        return missingFilesMap;
    }

    protected void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
