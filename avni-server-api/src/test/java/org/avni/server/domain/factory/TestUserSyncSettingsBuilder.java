package org.avni.server.domain.factory;

import org.avni.server.web.request.syncAttribute.UserSyncSettings;

import java.util.List;

public class TestUserSyncSettingsBuilder {
    private final UserSyncSettings entity = new UserSyncSettings();

    public TestUserSyncSettingsBuilder setSubjectTypeUUID(String subjectTypeUUID) {
        entity.setSubjectTypeUUID(subjectTypeUUID);
        return this;
    }

    public TestUserSyncSettingsBuilder setSyncConcept1(String syncConcept1) {
        entity.setSyncConcept1(syncConcept1);
        return this;
    }

    public TestUserSyncSettingsBuilder setSyncConcept1Values(List<String> syncConcept1Values) {
        entity.setSyncConcept1Values(syncConcept1Values);
        return this;
    }

    public TestUserSyncSettingsBuilder setSyncConcept2(String syncConcept2) {
        entity.setSyncConcept2(syncConcept2);
        return this;
    }

    public TestUserSyncSettingsBuilder setSyncConcept2Values(List<String> syncConcept2Values) {
        entity.setSyncConcept2Values(syncConcept2Values);
        return this;
    }

    public UserSyncSettings build() {
        return entity;
    }
}
