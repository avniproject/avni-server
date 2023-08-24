package org.avni.server.domain;

import org.avni.server.util.S;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonObject extends HashMap<String, Object> implements Serializable {
    public JsonObject() {
    }

    public JsonObject(Map<String, Object> map) {
        this.putAll(map);
    }

    public JsonObject with(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public JsonObject withEmptyCheck(String key, String value){
        if(!S.isEmpty(value)){
            super.put(key, value);
        }
        return this;
    }

    public List getList(String key) {
        return (List) get(key);
    }
}
