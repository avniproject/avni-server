package org.avni.server.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetadataDiffOutputGenerator {
    MetadataDiffChecker metadataDiffChecker;

    protected Map<String, Object> createFieldDiff(Object oldValue, Object newValue, String changeType) {
        Map<String, Object> fieldDiff = new LinkedHashMap<>();

        if(!"noModification".equals(changeType)) {
            if (oldValue == null && newValue != null) {
                fieldDiff.put("dataType", getDataType(newValue));
            } else if (oldValue != null && newValue == null) {
                fieldDiff.put("dataType", getDataType(oldValue));
            } else if (oldValue != null && newValue != null) {
                fieldDiff.put("dataType", getDataType(newValue));
            } else {
                fieldDiff.put("dataType", "object");
            }
        }
        fieldDiff.put("changeType", changeType);
        if (oldValue != null) {
            fieldDiff.put("oldValue", oldValue);
        }
        if (newValue != null) {
            fieldDiff.put("newValue", newValue);
        }
        return fieldDiff;
    }

    protected Map<String, Object> createObjectDiff(Map<String, Object> oldValue, Map<String, Object> newValue, String changeType) {
        Map<String, Object> objectDiff = new LinkedHashMap<>();
        Map<String, Object> fieldsDiff = metadataDiffChecker.findDifferences(oldValue, newValue);

        if (!fieldsDiff.isEmpty() && !"noModification".equals(changeType)) {
            objectDiff.put("dataType", "object");
            objectDiff.put("changeType", changeType);
            objectDiff.put("fields", fieldsDiff);
        }
        return objectDiff;
    }

    protected Map<String, Object> createArrayDiff(List<Object> oldValue, List<Object> newValue, String changeType) {
        Map<String, Object> arrayDiff = new LinkedHashMap<>();

        List<Map<String, Object>> itemsDiff = metadataDiffChecker.findArrayDifferences(oldValue, newValue);
        if (!itemsDiff.isEmpty() && !"noModification".equals(changeType)) {
            arrayDiff.put("dataType", "array");
            arrayDiff.put("changeType", changeType);
            arrayDiff.put("items", itemsDiff);
        }
        return arrayDiff;
    }
    private String getDataType(Object value) {
        if (value instanceof Map) {
            return "object";
        } else if (value instanceof List) {
            return "array";
        } else {
            return "primitive";
        }
    }
}
