package org.avni.server.service;

import org.avni.server.domain.metadata.ObjectCollectionChangeReport;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetadataDiffCheckerTest {
    private static final String CONCEPT_UUID = "2b0c5a52-7d3e-4c41-9a51-0d6b1f3e8c11";

    @Test
    public void testFindDifferences() {
        Map<String, Object> jsonMap1 = new HashMap<>();
        Map<String, Object> jsonMap2 = new HashMap<>();

        jsonMap1.put("uuid1", createJsonObject("value1"));
        jsonMap2.put("uuid1", createJsonObject("value2"));
        jsonMap2.put("uuid2", createJsonObject("value3"));

        MetadataDiffChecker metadataDiffChecker = new MetadataDiffChecker();
        ObjectCollectionChangeReport collectionChangeReport = metadataDiffChecker.findCollectionDifference(jsonMap1, jsonMap2);
        assertTrue(collectionChangeReport.hasChangeIn("uuid1"));
        assertTrue(collectionChangeReport.hasChangeIn("uuid2"));
    }

    // Key-values carry no uuid. A bundle that un-hides a concept whose first key-value is unchanged
    // must still count as a change, or the bundle import skips the concept (avni-webapp#1810).
    @Test
    public void aKeyValueRemovedBelowTheFirstIsAChange() {
        assertTrue(conceptChanged("keyValues",
                List.of(keyValue("verifyPhoneNumber", true)),
                List.of(keyValue("verifyPhoneNumber", true), keyValue("hidden", true))));
    }

    @Test
    public void aKeyValueAddedBelowTheFirstIsAChange() {
        assertTrue(conceptChanged("keyValues",
                List.of(keyValue("verifyPhoneNumber", true), keyValue("hidden", true)),
                List.of(keyValue("verifyPhoneNumber", true))));
    }

    @Test
    public void aChangedValueInALaterKeyValueIsAChange() {
        assertTrue(conceptChanged("keyValues",
                List.of(keyValue("isWithinCatchment", true), keyValue("lowestAddressLevelTypeUUIDs", List.of("village-uuid"))),
                List.of(keyValue("isWithinCatchment", true), keyValue("lowestAddressLevelTypeUUIDs", List.of("district-uuid")))));
    }

    @Test
    public void theSameKeyValuesAreNoChange() {
        assertFalse(conceptChanged("keyValues",
                List.of(keyValue("verifyPhoneNumber", true), keyValue("hidden", true)),
                List.of(keyValue("verifyPhoneNumber", true), keyValue("hidden", true))));
    }

    @Test
    public void answersInADifferentOrderAreNoChange() {
        assertFalse(conceptChanged("answers",
                List.of(answer("yes-uuid", "Yes"), answer("no-uuid", "No")),
                List.of(answer("no-uuid", "No"), answer("yes-uuid", "Yes"))));
    }

    @Test
    public void aRenamedAnswerIsAChange() {
        assertTrue(conceptChanged("answers",
                List.of(answer("yes-uuid", "Yes"), answer("no-uuid", "No")),
                List.of(answer("yes-uuid", "Yes"), answer("no-uuid", "Nope"))));
    }

    private boolean conceptChanged(String field, List<?> candidateItems, List<?> existingItems) {
        Map<String, Object> candidate = new HashMap<>();
        candidate.put(CONCEPT_UUID, concept(field, candidateItems));
        Map<String, Object> existing = new HashMap<>();
        existing.put(CONCEPT_UUID, concept(field, existingItems));
        return new MetadataDiffChecker().findCollectionDifference(candidate, existing).hasChangeIn(CONCEPT_UUID);
    }

    private Map<String, Object> concept(String field, List<?> items) {
        Map<String, Object> concept = new HashMap<>();
        concept.put("uuid", CONCEPT_UUID);
        concept.put("name", "Phone number");
        concept.put(field, items);
        return concept;
    }

    private Map<String, Object> keyValue(String key, Object value) {
        Map<String, Object> keyValue = new HashMap<>();
        keyValue.put("key", key);
        keyValue.put("value", value);
        return keyValue;
    }

    private Map<String, Object> answer(String uuid, String name) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("uuid", uuid);
        answer.put("name", name);
        return answer;
    }

    private Map<String, Object> createJsonObject(String value) {
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("key", value);
        return jsonObject;
    }
}
