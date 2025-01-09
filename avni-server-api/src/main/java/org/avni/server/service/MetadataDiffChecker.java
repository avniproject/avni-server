package org.avni.server.service;

import org.avni.server.domain.metadata.ChangeType;
import org.avni.server.domain.metadata.ObjectChangeReport;
import org.avni.server.domain.metadata.FieldChangeReport;
import org.avni.server.domain.metadata.ObjectCollectionChangeReport;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetadataDiffChecker {
    public ObjectCollectionChangeReport findCollectionDifference(Map<String, Object> incumbentEntityEntries, Map<String, Object> existingConfigEntityEntries) {
        ObjectCollectionChangeReport collectionChangeReport = new ObjectCollectionChangeReport();
        for (Map.Entry<String, Object> incumbentEntityEntry : incumbentEntityEntries.entrySet()) {
            String uuid = incumbentEntityEntry.getKey();
            Map<String, Object> incumbentEntity = (Map<String, Object>) incumbentEntityEntry.getValue();
            Object existingEntity = existingConfigEntityEntries.get(uuid);
            if (existingEntity == null) {
                collectionChangeReport.addObjectReport(ObjectChangeReport.added(uuid, incumbentEntity));
            } else {
                ObjectChangeReport diff = findObjectDifference(uuid, incumbentEntity, (Map<String, Object>) existingEntity);
                collectionChangeReport.addObjectReport(diff);
            }
        }

        for (Map.Entry<String, Object> existingEntry : existingConfigEntityEntries.entrySet()) {
            String existingConfigUUID = existingEntry.getKey();
            if (!incumbentEntityEntries.containsKey(existingConfigUUID)) {
                collectionChangeReport.addObjectReport(ObjectChangeReport.removed(existingConfigUUID, existingEntry.getValue()));
            }
        }
        return collectionChangeReport;
    }

    private ObjectChangeReport findObjectDifference(String parentObjectUuid, Map<String, Object> incumbentObject, Map<String, Object> existingObject) {
        ObjectChangeReport objectChangeReport = ObjectChangeReport.noChange(parentObjectUuid);
        for (Map.Entry<String, Object> incumbentFieldEntry : incumbentObject.entrySet()) {
            String incumbentFieldName = incumbentFieldEntry.getKey();
            Object incumbentFieldValue = incumbentFieldEntry.getValue();
            Object existingConfigFieldValue = existingObject.get(incumbentFieldName);

            if (incumbentFieldName.equals("id")) {
                continue;
            }

            if (incumbentFieldValue == null && existingConfigFieldValue == null) {
                continue;
            } else if (existingConfigFieldValue == null && incumbentFieldValue != null) {
                objectChangeReport.addFieldReport(FieldChangeReport.added(incumbentFieldName));
            } else if (existingConfigFieldValue != null && incumbentFieldValue == null) {
                objectChangeReport.addFieldReport(FieldChangeReport.removed(incumbentFieldName));
            } else {
                if (incumbentFieldValue instanceof Map && existingConfigFieldValue instanceof Map) {
                    ObjectChangeReport subObjectReport = findObjectDifference(parentObjectUuid, (Map<String, Object>) incumbentFieldValue,
                            (Map<String, Object>) existingConfigFieldValue);
                    if (!subObjectReport.getChangeType().equals(ChangeType.NoChange)) {
                        objectChangeReport.addFieldReport(FieldChangeReport.objectModified(incumbentFieldName, subObjectReport));
                    }
                } else if (incumbentFieldValue instanceof List && existingConfigFieldValue instanceof List) {
                    ObjectCollectionChangeReport collectionChangeReport = findArrayDifferences((List<Object>) incumbentFieldValue, (List<Object>) existingConfigFieldValue);
                    if (!collectionChangeReport.hasNoChange()) {
                        objectChangeReport.addFieldReport(FieldChangeReport.collectionModified(incumbentFieldName, collectionChangeReport));
                    }
                } else if (!Objects.equals(incumbentFieldValue, existingConfigFieldValue)) {
                    objectChangeReport.addFieldReport(FieldChangeReport.modified(incumbentFieldName, existingConfigFieldValue, incumbentFieldValue));
                }
            }
        }

        for (Map.Entry<String, Object> existingFieldEntry : existingObject.entrySet()) {
            String existingFieldName = existingFieldEntry.getKey();
            if (!incumbentObject.containsKey(existingFieldName)) {
                objectChangeReport.addFieldReport(FieldChangeReport.removed(existingFieldName));
            }
        }

        return objectChangeReport;
    }

    private ObjectCollectionChangeReport findArrayDifferences(List<Object> incumbentArray, List<Object> existingConfigArray) {
        Function<Map<String, Object>, String> getUuid = obj -> (String) obj.get("uuid");

        Map<String, Object> incumbentObjects = incumbentArray.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        Map<String, Object> existingConfigObjects = existingConfigArray.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        return this.findCollectionDifference(incumbentObjects, existingConfigObjects);
    }
}
