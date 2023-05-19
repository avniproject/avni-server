package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ObservationCollection extends HashMap<String, Object> implements Serializable {

    public static final String SPLIT_REGEX = "[,]+";
    public static final Pattern SPLIT_PATTERN = Pattern.compile(SPLIT_REGEX);
    public static final String REPLACE_REGEX = "\\[|\\]";
    public static final String EMPTY_STRING = "";
    public static final int INT_CONSTANT_ONE = 1;
    public static final int INT_CONSTANT_ZERO = 0;

    public ObservationCollection() {
    }

    public ObservationCollection(Map<String, Object> observations) {
        this.putAll(observations);
    }

    public String getStringValue(Object key) {
        Object value = this.getOrDefault(key, null);
       return value == null ? null : value.toString();
    }

    public String getObjectAsSingleStringValue(Object key) {
        return getAsSingleStringValue(getStringValue(key));
    }

    public String getAsSingleStringValue(String arrayValue) {
        if(!StringUtils.hasText(arrayValue)) {
            return arrayValue;
        }
        String[] values = SPLIT_PATTERN.split(arrayValue.replaceAll(REPLACE_REGEX, EMPTY_STRING));
        return values.length != INT_CONSTANT_ONE ? arrayValue : values[INT_CONSTANT_ZERO];
    }

    public String[] getConceptUUIDs() {
        return this.keySet().toArray(new String[0]);
    }

    public Map<Concept, Object> filterByConcepts(List<Concept> mediaConcepts) {
        Map<Concept, Object> map = new HashMap<>();
        for (Concept concept : mediaConcepts) {
            if (this.containsKey(concept.getUuid()))
                map.put(concept, this.get(concept.getUuid()));
        }
        return map;
    }
}
