package org.avni.server.domain.factory;

import org.avni.server.domain.*;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.joda.time.DateTime;

import java.util.UUID;

public class UserBuilder {
    private final User user;

    public UserBuilder() {
        this(new User());
    }

    public UserBuilder(User user) {
        this.user = user;
    }

    public UserBuilder id(long id) {
        user.setId(id);
        return this;
    }

    public UserBuilder isAdmin(boolean isAdmin) {
        user.setAdmin(isAdmin);
        return this;
    }

    public UserBuilder userName(String name) {
        user.setUsername(name);
        return this;
    }

    public UserBuilder phoneNumber(String phoneNumber) {
        user.setPhoneNumber(phoneNumber);
        return this;
    }

    public UserBuilder organisationId(long orgId) {
        user.setOrganisationId(orgId);
        return this;
    }

    public UserBuilder withUuid(String uuid) {
        user.setUuid(uuid);
        return this;
    }

    public UserBuilder withOperatingIndividualScope(OperatingIndividualScope operatingIndividualScope) {
        user.setOperatingIndividualScope(operatingIndividualScope);
    	return this;
    }

    public UserBuilder withDefaultValuesForNewEntity() {
        String placeholder = UUID.randomUUID().toString();
        user.setCreatedDateTime(DateTime.now());
        user.setLastModifiedDateTime(DateTime.now());
        return userName(placeholder).phoneNumber(placeholder.substring(0, 10)).withUuid(placeholder).withOperatingIndividualScope(OperatingIndividualScope.None);
    }

    public UserBuilder withAuditUser(User auditUser) {
        this.user.setCreatedBy(auditUser);
        this.user.setLastModifiedBy(auditUser);
    	return this;
    }

    public UserBuilder withCatchment(Catchment catchment) {
        this.user.setCatchment(catchment);
    	return this;
    }

    public UserBuilder withSubjectTypeSyncSettings(UserSyncSettings ... userSyncSettings) {
        this.user.setSyncSettings(new JsonObject().with(User.SyncSettingKeys.subjectTypeSyncSettings.name(), userSyncSettings));
        return this;
    }

    public UserBuilder setSettings(JsonObject settings) {
        user.setSettings(settings);
        return this;
    }

    public User build() {
        return user;
    }
}
