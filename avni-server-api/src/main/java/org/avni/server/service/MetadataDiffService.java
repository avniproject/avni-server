package org.avni.server.service;

import org.avni.server.domain.Organisation;
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

    public Map<String, Object> findChangesInBundle(MultipartFile incumbentBundleFile) {
        Map<String, Object> result = new HashMap<>();
        File tempDir1 = null, tempDir2 = null;

        try {
            Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
            tempDir1 = bundleAndFileHandler.extractZip(incumbentBundleFile);
            ByteArrayOutputStream bundleOutputStream = bundleService.createBundle(organisation, false);
            ByteArrayInputStream bundleInputStream = new ByteArrayInputStream(bundleOutputStream.toByteArray());
            tempDir2 = bundleAndFileHandler.extractZip(bundleInputStream);

            List<File> files1 = bundleAndFileHandler.listJsonFiles(tempDir1);
            List<File> files2 = bundleAndFileHandler.listJsonFiles(tempDir2);

            Map<String, Map<String, Object>> jsonMap1 = bundleAndFileHandler.parseJsonFiles(files1, tempDir1);
            Map<String, Map<String, Object>> jsonMap2 = bundleAndFileHandler.parseJsonFiles(files2, tempDir2);

            Set<String> fileNames1 = jsonMap1.keySet();
            Set<String> fileNames2 = jsonMap2.keySet();

            Set<String> commonFileNames = new HashSet<>(fileNames1);
            commonFileNames.retainAll(fileNames2);

            for (String fileName : commonFileNames) {
                Map<String, Object> jsonMapFile1 = jsonMap1.get(fileName);
                Map<String, Object> jsonMapFile2 = jsonMap2.get(fileName);

                if (jsonMapFile1 != null && jsonMapFile2 != null) {
                    Map<String, Object> fileDifferences = diffChecker.findDifferences(jsonMapFile1, jsonMapFile2);
                    if (!fileDifferences.isEmpty()) {
                        result.put(fileName, fileDifferences);
                    }
                }
            }

            Set<String> missingInZip1 = findMissingFiles(fileNames1, fileNames2);
            if (!missingInZip1.isEmpty()) {
                result.putAll(missingFilesMap(missingInZip1, "Missing Files in UAT ZIP"));
            }

            Set<String> missingInZip2 = findMissingFiles(fileNames2, fileNames1);
            if (!missingInZip2.isEmpty()) {
                result.putAll(missingFilesMap(missingInZip2, "Missing Files in PROD ZIP"));
            }

        } catch (IOException e) {
            logger.error("Error comparing metadata ZIPs: " + e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Error comparing metadata ZIPs: " + e.getMessage());
            result.put("error", errorResult);
        } finally {
            if (tempDir1 != null) {
                deleteDirectory(tempDir1);
            }
            if (tempDir2 != null) {
                deleteDirectory(tempDir2);
            }
        }
        return result;
    }

    protected Set<String> findMissingFiles(Set<String> fileNames1, Set<String> fileNames2) {
        Set<String> missingFiles = new HashSet<>(fileNames1);
        missingFiles.removeAll(fileNames2);
        return missingFiles;
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
