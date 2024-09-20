package org.avni.server.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetadataDiffOutputGenerator {

    public static final String NO_MODIFICATION = "noModification";
    public static final String OLD_VALUE = "oldValue";
    public static final String NEW_VALUE = "newValue";
    public static final String CHANGE_TYPE = "changeType";
    public static final String DATA_TYPE = "dataType";
    public static final String OBJECT = "object";
    public static final String FIELD = "field";
    public static final String ARRAY = "array";
    public static final String PRIMITIVE = "primitive";
    public static final String ITEMS = "items";

    protected Map<String, Object> createFieldDiff(Object oldValue, Object newValue, String changeType) {
        Map<String, Object> fieldDiff = new LinkedHashMap<>();

        if(!NO_MODIFICATION.equals(changeType)) {
            if (oldValue == null && newValue != null) {
                fieldDiff.put(DATA_TYPE, getDataType(newValue));
            } else if (oldValue != null && newValue == null) {
                fieldDiff.put(DATA_TYPE, getDataType(oldValue));
            } else if (oldValue != null && newValue != null) {
                fieldDiff.put(DATA_TYPE, getDataType(newValue));
            } else {
                fieldDiff.put(DATA_TYPE, OBJECT);
            }
        }
        fieldDiff.put(CHANGE_TYPE, changeType);
        if (oldValue != null) {
            fieldDiff.put(OLD_VALUE, oldValue);
        }
        if (newValue != null) {
            fieldDiff.put(NEW_VALUE, newValue);
        }
        return fieldDiff;
    }

    protected Map<String, Object> createObjectDiff(Map<String, Object> fieldsDiff, String changeType) {
        Map<String, Object> objectDiff = new LinkedHashMap<>();

        if (!fieldsDiff.isEmpty() && !NO_MODIFICATION.equals(changeType)) {
            objectDiff.put(DATA_TYPE, OBJECT);
            objectDiff.put(CHANGE_TYPE, changeType);
            objectDiff.put(FIELD, fieldsDiff);
        }
        return objectDiff;
    }

    protected Map<String, Object> createArrayDiff(List<Map<String, Object>> itemsDiff, String changeType) {
        Map<String, Object> arrayDiff = new LinkedHashMap<>();

        if (!itemsDiff.isEmpty() && !NO_MODIFICATION.equals(changeType)) {
            arrayDiff.put(DATA_TYPE, ARRAY);
            arrayDiff.put(CHANGE_TYPE, changeType);
            arrayDiff.put(ITEMS, itemsDiff);
        }
        return arrayDiff;
    }
    private String getDataType(Object value) {
        if (value instanceof Map) {
            return OBJECT;
        } else if (value instanceof List) {
            return ARRAY;
        } else {
            return PRIMITIVE;
        }
    }
}
