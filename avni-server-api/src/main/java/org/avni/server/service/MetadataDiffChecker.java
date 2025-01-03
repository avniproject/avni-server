package org.avni.server.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MetadataDiffChecker {

    public static final String MODIFIED = "modified";
    public static final String ADDED = "added";
    public static final String REMOVED = "removed";
    public static final String NO_MODIFICATION = "noModification";

    MetadataDiffOutputGenerator metadataDiffOutputGenerator;

    public MetadataDiffChecker(MetadataDiffOutputGenerator metadataDiffOutputGenerator) {
        this.metadataDiffOutputGenerator = metadataDiffOutputGenerator;
    }

    public Map<String, Object> findDifferences(Map<String, Object> incumbentJsonValues, Map<String, Object> existingConfigJsonValues) {
        Map<String, Object> differences = new HashMap<>();
        boolean hasDifferences = false;
        String uuid = "null";
        for (Map.Entry<String, Object> incumbentJsonEntry : incumbentJsonValues.entrySet()) {
            uuid = incumbentJsonEntry.getKey();
            Object incumbentJsonValue = incumbentJsonEntry.getValue();
            Object existingConfigJsonValue = existingConfigJsonValues.get(uuid);
            if (existingConfigJsonValue != null) {
                Map<String, Object> diff = findJsonDifferences(castToStringObjectMap(incumbentJsonValue), castToStringObjectMap(existingConfigJsonValue));
                if (!diff.isEmpty()) {
                    differences.put(uuid, diff);
                    hasDifferences = true;
                }
            } else {
                differences.put(uuid, metadataDiffOutputGenerator.createFieldDiff(incumbentJsonValue, null, REMOVED));
                hasDifferences = true;
            }
        }

        for (Map.Entry<String, Object> entry : existingConfigJsonValues.entrySet()) {
            String existingConfigUUID = entry.getKey();
            if (!incumbentJsonValues.containsKey(existingConfigUUID)) {
                differences.put(existingConfigUUID, metadataDiffOutputGenerator.createFieldDiff(null, entry.getValue(), ADDED));
                hasDifferences = true;
            }
        }

        if (!hasDifferences) {
            differences.put(uuid, metadataDiffOutputGenerator.createFieldDiff(null, null, NO_MODIFICATION));
        }
        return differences;
    }

    protected Map<String, Object> findJsonDifferences(Map<String, Object> incumbentJsonValues, Map<String, Object> existingConfigJsonValues) {
        Map<String, Object> differences = new LinkedHashMap<>();
        if (incumbentJsonValues == null && existingConfigJsonValues == null) {
            return differences;
        }

        if (incumbentJsonValues == null) {
            existingConfigJsonValues.forEach((key, value) -> differences.put(key, metadataDiffOutputGenerator.createFieldDiff(null, value, ADDED)));
            return differences;
        }

        if (existingConfigJsonValues == null) {
            incumbentJsonValues.forEach((key, value) -> differences.put(key, metadataDiffOutputGenerator.createFieldDiff(value, null, REMOVED)));
            return differences;
        }

        for (Map.Entry<String, Object> incumbentJsonEntry : incumbentJsonValues.entrySet()) {
            String incumbentJsonKey = incumbentJsonEntry.getKey();
            Object incumbentJsonValue = incumbentJsonEntry.getValue();
            Object existingConfigValue = existingConfigJsonValues.get(incumbentJsonKey);

            if (incumbentJsonKey.equals("id")) {
                continue;
            }
            if (existingConfigValue == null && incumbentJsonValue != null) {
                differences.put(incumbentJsonKey, metadataDiffOutputGenerator.createFieldDiff(incumbentJsonValue, null, ADDED));
            } else if (existingConfigValue != null && incumbentJsonValue == null) {
                differences.put(incumbentJsonKey, metadataDiffOutputGenerator.createFieldDiff(incumbentJsonValue, null, REMOVED));
            } else {
                if (incumbentJsonValue instanceof Map && existingConfigValue instanceof Map) {
                    Map<String, Object> subDiff = findJsonDifferences((Map<String, Object>) incumbentJsonValue, (Map<String, Object>) existingConfigValue);
                    if (!subDiff.isEmpty()) {
                        differences.put(incumbentJsonKey, metadataDiffOutputGenerator.createObjectDiff(subDiff, MODIFIED));
                    }
                } else if (incumbentJsonValue instanceof List && existingConfigValue instanceof List) {
                    List<Map<String, Object>> listDiff = findArrayDifferences((List<Object>) incumbentJsonValue, (List<Object>) existingConfigValue);
                    if (!listDiff.isEmpty()) {
                        differences.put(incumbentJsonKey, metadataDiffOutputGenerator.createArrayDiff(listDiff, MODIFIED));
                    }
                } else if (!Objects.equals(incumbentJsonValue, existingConfigValue)) {
                    differences.put(incumbentJsonKey, metadataDiffOutputGenerator.createFieldDiff(incumbentJsonValue, existingConfigValue, MODIFIED));
                }
            }
        }

        for (Map.Entry<String, Object> entry : existingConfigJsonValues.entrySet()) {
            String key = entry.getKey();
            if (!incumbentJsonValues.containsKey(key)) {
                differences.put(key, metadataDiffOutputGenerator.createFieldDiff(null, entry.getValue(), ADDED));
            }
        }

        return differences;
    }

    protected List<Map<String, Object>> findArrayDifferences(List<Object> array1, List<Object> array2) {
        List<Map<String, Object>> differences = new ArrayList<>();

        Function<Map<String, Object>, String> getUuid = obj -> (String) obj.get("uuid");

        Map<String, Map<String, Object>> map1 = array1.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        Map<String, Map<String, Object>> map2 = array2.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        for (String uuid : map2.keySet()) {
            if (!map1.containsKey(uuid)) {
                differences.add(metadataDiffOutputGenerator.createFieldDiff(null, map2.get(uuid), ADDED));
            } else {
                Map<String, Object> obj1 = map1.get(uuid);
                Map<String, Object> obj2 = map2.get(uuid);

                Map<String, Object> subDiff = findJsonDifferences(obj1, obj2);
                if (!subDiff.isEmpty()) {
                    differences.add(metadataDiffOutputGenerator.createObjectDiff(subDiff, MODIFIED));
                }
            }
        }

        for (String uuid : map1.keySet()) {
            if (!map2.containsKey(uuid)) {
                differences.add(metadataDiffOutputGenerator.createFieldDiff(map1.get(uuid), null, REMOVED));
            }
        }
        return differences;
    }

    private Map<String, Object> castToStringObjectMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return new HashMap<>();
    }
}
