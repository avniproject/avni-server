package org.avni.server.util;

import java.util.HashMap;
import java.util.Map;

public class MapUtil {
    public static Map<String, Object> getValueFromPath(Map<String, Object> map, String[] path) {
        Map<String, Object> returnValue = map;
        for (String key : path) {
            returnValue = (Map<String, Object>) returnValue.get(key);
        }
        return returnValue;
    }

    public static Map<String, Object> setValueAtPath(Map<String, Object> map, String[] path, Object value) {
        Map<String, Object> currentMap = map;
        for (int i = 0; i < path.length - 1; i++) {
            String key = path[i];
            currentMap.put(key, new HashMap<String, Object>());
            currentMap = (Map<String, Object>) currentMap.get(key);
        }
        currentMap.put(path[path.length - 1], value);
        return map;
    }
}
