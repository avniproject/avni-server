package org.avni.server.service;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.metadata.MetadataChangeReport;
import org.avni.server.domain.metadata.ObjectCollectionChangeReport;
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
    public MetadataDiffService(MetadataBundleAndFileHandler bundleAndFileHandler, BundleService bundleService) {
        this.bundleAndFileHandler = bundleAndFileHandler;
        this.diffChecker = new MetadataDiffChecker();
        this.bundleService = bundleService;
    }

    public MetadataChangeReport findChangesInBundle(MultipartFile candidateBundleFile) {
        MetadataChangeReport metadataChangeReport = new MetadataChangeReport();
        File candidateDir = null, existingDir = null;

        try {
            Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
            candidateDir = bundleAndFileHandler.extractZip(candidateBundleFile);
            ByteArrayOutputStream bundleOutputStream = this.bundleService.createBundle(organisation, false);
            ByteArrayInputStream stream = new ByteArrayInputStream(bundleOutputStream.toByteArray());
            existingDir = bundleAndFileHandler.extractZip(stream);

            List<File> candidateFiles = bundleAndFileHandler.listJsonFiles(candidateDir);
            List<File> existingFiles = bundleAndFileHandler.listJsonFiles(existingDir);

            Map<String, Map<String, Object>> candidateJsons = bundleAndFileHandler.parseJsonFiles(candidateFiles, candidateDir);
            Map<String, Map<String, Object>> existingJsons = bundleAndFileHandler.parseJsonFiles(existingFiles, existingDir);

            Set<String> candidateJsonFiles = candidateJsons.keySet();
            Set<String> existingJsonFiles = existingJsons.keySet();

            Set<String> commonFileNames = new HashSet<>(candidateJsonFiles);
            commonFileNames.retainAll(existingJsonFiles);

            for (String fileName : commonFileNames) {
                Map<String, Object> candidateJsonFile = candidateJsons.get(fileName);
                Map<String, Object> existingJsonFile = existingJsons.get(fileName);

                if (candidateJsonFile != null && existingJsonFile != null) {
                    ObjectCollectionChangeReport fileReport = diffChecker.findCollectionDifference(candidateJsonFile, existingJsonFile);
                    if (!fileReport.hasNoChange()) {
                        metadataChangeReport.addChange(fileName, fileReport);
                    }
                }
            }

            Set<String> filesMissingInCandidate = findMissingFiles(candidateJsonFiles, existingJsonFiles);
            if (!filesMissingInCandidate.isEmpty()) {
                metadataChangeReport.setMissingInNew(filesMissingInCandidate);
            }

            Set<String> filesMissingInExisting = findMissingFiles(existingJsonFiles, candidateJsonFiles);
            if (!filesMissingInExisting.isEmpty()) {
                metadataChangeReport.setMissingInExisting(filesMissingInExisting);
            }
        } catch (IOException e) {
            logger.error("Error comparing metadata ZIPs: " + e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Error comparing metadata ZIPs: " + e.getMessage());
            metadataChangeReport.setError(errorResult);
        } finally {
            if (candidateDir != null) {
                deleteDirectory(candidateDir);
            }
            if (existingDir != null) {
                deleteDirectory(existingDir);
            }
        }
        return metadataChangeReport;
    }

    protected Set<String> findMissingFiles(Set<String> candidateJsonFiles, Set<String> existingJsonFiles) {
        Set<String> missingInCandidateFiles = new HashSet<>(candidateJsonFiles);
        missingInCandidateFiles.removeAll(existingJsonFiles);
        return missingInCandidateFiles;
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
