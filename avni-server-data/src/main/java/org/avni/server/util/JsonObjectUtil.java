package org.avni.server.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.User.SyncSettingKeys;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonObjectUtil {

    public static Map<String, String> toStringMap(JsonObject source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((k, v) -> result.put(k, v == null ? "" : v.toString()));
        return result;
    }

    public static List<String> getSyncAttributeValuesBySubjectTypeUUID(JsonObject syncSettings, String subjectTypeUUID, SyncSettingKeys syncAttribute) {
        List<UserSyncSettings> userSyncSettings =getUserSyncSettings(syncSettings);
        UserSyncSettings subjectTypeSyncSettings = userSyncSettings.stream().filter(userSyncSetting -> subjectTypeUUID.equals(userSyncSetting.getSubjectTypeUUID())).findFirst().orElse(null);
        if (subjectTypeSyncSettings != null) {
            switch (syncAttribute) {
                case syncAttribute1:
                    return subjectTypeSyncSettings.getSyncConcept1Values();
                case syncAttribute2:
                    return subjectTypeSyncSettings.getSyncConcept2Values();
                default:
                    return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    public static List<UserSyncSettings> getUserSyncSettings(JsonObject syncSettings) {
        if (syncSettings != null && syncSettings.containsKey(SyncSettingKeys.subjectTypeSyncSettings.name())) {
            ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();
            return objectMapper.convertValue(syncSettings.get(SyncSettingKeys.subjectTypeSyncSettings.name()), new TypeReference<List<UserSyncSettings>>() {
            });
        }
        return Collections.emptyList();
    }
}
