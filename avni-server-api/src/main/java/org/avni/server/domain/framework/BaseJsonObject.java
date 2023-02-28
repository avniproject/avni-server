package org.avni.server.domain.framework;

import java.util.Map;

public class BaseJsonObject {
    protected final Map<String, Object> map;

    public BaseJsonObject(Map<String, Object> map) {
        this.map = map;
    }

    public String getStringValue(String fieldName) {
        return (String) map.get(fieldName);
    }
}
