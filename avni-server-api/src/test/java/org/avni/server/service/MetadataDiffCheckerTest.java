package org.avni.server.service;

import org.avni.server.domain.metadata.ObjectCollectionChangeReport;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class MetadataDiffCheckerTest {
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

    private Map<String, Object> createJsonObject(String value) {
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("key", value);
        return jsonObject;
    }
}
