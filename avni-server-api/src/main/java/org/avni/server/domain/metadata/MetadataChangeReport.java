package org.avni.server.domain.metadata;

import java.util.*;

public class MetadataChangeReport {
    private final Map<String, ObjectCollectionChangeReport> fileChangeReports = new HashMap<>();
    private List<String> missingFilesTypeInNew;
    private List<String> missingFilesInExisting;
    private Map<String, Object> errors;

    public int getNumberOfModifications() {
        return fileChangeReports.entrySet().stream().reduce(0, (total, entry) -> total + (entry.getValue().hasNoChange() ? 0 : 1), Integer::sum);
    }

    private static void getAllValues(Map<String, Object> map, List<Object> values) {
        map.entrySet().forEach(entry -> {
            if (entry.getValue() instanceof Map) {
                getAllValues((Map<String, Object>) entry.getValue(), values);
            } else {
                values.add(entry.getValue());
            }
        });
    }

    public void addChange(String fileName, ObjectCollectionChangeReport collectionChangeReport) {
        fileChangeReports.put(fileName, collectionChangeReport);
    }

    public void setMissingInExisting(Set<String> missing) {
        missingFilesInExisting = new ArrayList<>(missing);
    }

    public void setMissingInNew(Set<String> missing) {
        missingFilesTypeInNew = new ArrayList<>(missing);
    }

    public void setError(Map<String, Object> errorResult) {
        this.errors = errorResult;
    }

    public Map<String, ObjectCollectionChangeReport> getFileChangeReports() {
        return fileChangeReports;
    }

    public List<String> getMissingFilesTypeInNew() {
        return missingFilesTypeInNew;
    }

    public List<String> getMissingFilesInExisting() {
        return missingFilesInExisting;
    }

    public Map<String, Object> getErrors() {
        return errors;
    }
}
