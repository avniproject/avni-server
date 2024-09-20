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

    protected Map<String, Object> findDifferences(Map<String, Object> jsonMap1, Map<String, Object> jsonMap2) {
        Map<String, Object> differences = new HashMap<>();
        boolean hasDifferences = false;
        String uuid = "null";
        for (Map.Entry<String, Object> entry : jsonMap1.entrySet()) {
            uuid = entry.getKey();
            Object json1 = entry.getValue();
            Object json2 = jsonMap2.get(uuid);
            if (json2 != null) {
                Map<String, Object> diff = findJsonDifferences(castToStringObjectMap(json1), castToStringObjectMap(json2));
                if (!diff.isEmpty()) {
                    differences.put(uuid, diff);
                    hasDifferences = true;
                }
            } else {
                differences.put(uuid, metadataDiffOutputGenerator.createFieldDiff( json1, null,REMOVED));
                hasDifferences = true;
            }
        }

        for (Map.Entry<String, Object> entry : jsonMap2.entrySet()) {
            String uuid2 = entry.getKey();
            if (!jsonMap1.containsKey(uuid2)) {
                differences.put(uuid2, metadataDiffOutputGenerator.createFieldDiff(null, entry.getValue(), ADDED));
                hasDifferences = true;
            }
        }

        if (!hasDifferences) {
            differences.put(uuid, metadataDiffOutputGenerator.createFieldDiff(null, null, NO_MODIFICATION));
        }
        return differences;
    }

    protected Map<String, Object> findJsonDifferences(Map<String, Object> json1, Map<String, Object> json2) {
        Map<String, Object> differences = new LinkedHashMap<>();
        if (json1 == null && json2 == null) {
            return differences;
        }

        if (json1 == null) {
            json2.forEach((key, value) -> differences.put(key, metadataDiffOutputGenerator.createFieldDiff(null, value, ADDED)));
            return differences;
        }

        if (json2 == null) {
            json1.forEach((key, value) -> differences.put(key, metadataDiffOutputGenerator.createFieldDiff(value, null, REMOVED)));
            return differences;
        }

        for (Map.Entry<String, Object> entry : json1.entrySet()) {
            String key = entry.getKey();
            Object value1 = entry.getValue();
            Object value2 = json2.get(key);

            if (key.equals("id")) {
                continue;
            }
            if (value2 == null) {
                differences.put(key, metadataDiffOutputGenerator.createFieldDiff(value1, null, REMOVED));
            } else {
                if (value1 instanceof Map && value2 instanceof Map) {
                    Map<String, Object> subDiff = findJsonDifferences((Map<String, Object>) value1, (Map<String, Object>) value2);
                    if (!subDiff.isEmpty()) {
                        differences.put(key, metadataDiffOutputGenerator.createObjectDiff(subDiff, MODIFIED));
                    }
                } else if (value1 instanceof List && value2 instanceof List) {
                    List<Map<String, Object>> listDiff = findArrayDifferences((List<Object>) value1, (List<Object>) value2);
                    if (!listDiff.isEmpty()) {
                        differences.put(key, metadataDiffOutputGenerator.createArrayDiff(listDiff, MODIFIED));
                    }
                } else if (!value1.equals(value2)) {
                    differences.put(key, metadataDiffOutputGenerator.createFieldDiff(value1, value2, MODIFIED));
                }
            }
        }

        for (Map.Entry<String, Object> entry : json2.entrySet()) {
            String key = entry.getKey();
            if (!json1.containsKey(key)) {
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
