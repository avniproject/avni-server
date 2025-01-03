package org.avni.server.domain.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetadataChangeReport extends HashMap<String, Object> {
    public int getNumberOfModifications() {
        ArrayList<Object> values = new ArrayList<>();
        getAllValues(this, values);
        values.removeIf(value -> value.equals("noModification"));
        return values.size();
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
}
