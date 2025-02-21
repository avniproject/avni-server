package org.avni.server.service;

import org.avni.server.domain.metadata.ChangeType;
import org.avni.server.domain.metadata.ObjectChangeReport;
import org.avni.server.domain.metadata.FieldChangeReport;
import org.avni.server.domain.metadata.ObjectCollectionChangeReport;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetadataDiffChecker {
    public ObjectCollectionChangeReport findCollectionDifference(Map<String, Object> candidateEntityEntries, Map<String, Object> existingEntityEntries) {
        ObjectCollectionChangeReport collectionChangeReport = new ObjectCollectionChangeReport();
        for (Map.Entry<String, Object> candidateEntityEntry : candidateEntityEntries.entrySet()) {
            String uuid = candidateEntityEntry.getKey();
            Map<String, Object> candidateEntity = (Map<String, Object>) candidateEntityEntry.getValue();
            Object existingEntity = existingEntityEntries.get(uuid);
            if (existingEntity == null) {
                collectionChangeReport.addObjectReport(ObjectChangeReport.added(uuid, candidateEntity));
            } else {
                ObjectChangeReport diff = findObjectDifference(uuid, candidateEntity, (Map<String, Object>) existingEntity);
                collectionChangeReport.addObjectReport(diff);
            }
        }

        for (Map.Entry<String, Object> existingEntry : existingEntityEntries.entrySet()) {
            String existingConfigUUID = existingEntry.getKey();
            if (!candidateEntityEntries.containsKey(existingConfigUUID)) {
                collectionChangeReport.addObjectReport(ObjectChangeReport.missing(existingConfigUUID, existingEntry.getValue()));
            }
        }
        return collectionChangeReport;
    }

    private ObjectChangeReport findObjectDifference(String parentObjectUuid, Map<String, Object> candidateObject, Map<String, Object> existingObject) {
        ObjectChangeReport objectChangeReport = ObjectChangeReport.noChange(parentObjectUuid);
        for (Map.Entry<String, Object> candidateFieldEntry : candidateObject.entrySet()) {
            String candidateFieldName = candidateFieldEntry.getKey();
            Object candidateFieldValue = candidateFieldEntry.getValue();
            Object existingFieldValue = existingObject.get(candidateFieldName);

            if (candidateFieldName.equals("id")) {
                continue;
            }

            if (candidateFieldValue == null && existingFieldValue == null) {
                continue;
            } else if (existingFieldValue == null && candidateFieldValue != null) {
                objectChangeReport.addFieldReport(FieldChangeReport.added(candidateFieldName));
            } else if (existingFieldValue != null && candidateFieldValue == null) {
                objectChangeReport.addFieldReport(FieldChangeReport.missing(candidateFieldName));
            } else {
                if (candidateFieldValue instanceof Map && existingFieldValue instanceof Map) {
                    ObjectChangeReport subObjectReport = findObjectDifference(parentObjectUuid, (Map<String, Object>) candidateFieldValue,
                            (Map<String, Object>) existingFieldValue);
                    if (!subObjectReport.getChangeType().equals(ChangeType.NoChange)) {
                        objectChangeReport.addFieldReport(FieldChangeReport.objectModified(candidateFieldName, subObjectReport));
                    }
                } else if (candidateFieldValue instanceof List && existingFieldValue instanceof List) {
                    ObjectCollectionChangeReport collectionChangeReport = findArrayDifferences((List<Object>) candidateFieldValue, (List<Object>) existingFieldValue);
                    if (!collectionChangeReport.hasNoChange()) {
                        objectChangeReport.addFieldReport(FieldChangeReport.collectionModified(candidateFieldName, collectionChangeReport));
                    }
                } else if (!Objects.equals(candidateFieldValue, existingFieldValue)) {
                    objectChangeReport.addFieldReport(FieldChangeReport.modified(candidateFieldName, existingFieldValue, candidateFieldValue));
                }
            }
        }

        for (Map.Entry<String, Object> existingFieldEntry : existingObject.entrySet()) {
            String existingFieldName = existingFieldEntry.getKey();
            if (!candidateObject.containsKey(existingFieldName)) {
                objectChangeReport.addFieldReport(FieldChangeReport.missing(existingFieldName));
            }
        }

        return objectChangeReport;
    }

    private ObjectCollectionChangeReport findArrayDifferences(List<Object> candidateArray, List<Object> existingArray) {
        Function<Map<String, Object>, String> getUuid = obj -> (String) obj.get("uuid");

        Map<String, Object> candidateObjects = candidateArray.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        Map<String, Object> existingObjects = existingArray.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        return this.findCollectionDifference(candidateObjects, existingObjects);
    }
}
