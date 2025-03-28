package org.avni.server.web.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OperatingIndividualScope;
import org.avni.server.domain.User;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserContract extends ReferenceDataContract {
    private String username;
    private Long catchmentId;
    private String phoneNumber;
    private String email;
    private boolean admin;
    private String operatingIndividualScope = OperatingIndividualScope.None.toString();
    private JsonObject settings;
    private Long organisationId;
    private List<Long> accountIds;
    private boolean disabledInCognito;
    private boolean ignoreSyncSettingsInDEA;
    private String password;
    private JsonObject syncSettings;
    private String createdBy;
    private String lastModifiedBy;
    private DateTime lastModifiedDateTime;
    private DateTime lastActivatedDateTime;
    private DateTime createdDateTime;
    private List<Long> groupIds;
    private List<String> userGroupNames;

    public static UserContract fromEntity(User user) {
        UserContract userContract = new UserContract();
        userContract.setId(user.getId());
        userContract.setName(user.getName());
        userContract.setUsername(user.getUsername());
        userContract.setEmail(user.getEmail());
        userContract.setPhoneNumber(user.getPhoneNumber());
        userContract.setOrganisationId(user.getOrganisationId());
        userContract.setDisabledInCognito(user.isDisabledInCognito());
        userContract.setIgnoreSyncSettingsInDEA(user.isIgnoreSyncSettingsInDEA());
        userContract.setCatchmentId(user.getCatchmentId().orElse(null));
        userContract.setSettings(user.getSettings());
        userContract.setCreatedBy(user.getCreatedByUserName());
        userContract.setCreatedDateTime(user.getCreatedDateTime().toDateTime());
        userContract.setLastModifiedBy(user.getLastModifiedByUserName());
        userContract.setLastModifiedDateTime(user.getLastModifiedDateTime().toDateTime());
        if (!user.isDisabledInCognito() && Objects.nonNull(user.getLastActivatedDateTime())) {
            userContract.setLastActivatedDateTime(user.getLastActivatedDateTime().toDateTime());
        }
        userContract.setGroupIds(user.getUserGroups().stream()
                .map(userGroup -> userGroup.getGroupId()).collect(Collectors.toList()));
        userContract.setUserGroupNames(user.getUserGroups().stream()
                .map(userGroup -> userGroup.getGroupName()).collect(Collectors.toList()));
        return userContract;
    }

    public boolean isDisabledInCognito() {
        return disabledInCognito;
    }

    public void setDisabledInCognito(boolean disabledInCognito) {
        this.disabledInCognito = disabledInCognito;
    }

    public boolean isIgnoreSyncSettingsInDEA() {
        return ignoreSyncSettingsInDEA;
    }

    public void setIgnoreSyncSettingsInDEA(boolean ignoreSyncSettingsInDEA) {
        this.ignoreSyncSettingsInDEA = ignoreSyncSettingsInDEA;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(Long organisationId) {
        this.organisationId = organisationId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username.trim() : null;
    }

    public Long getCatchmentId() {
        return catchmentId;
    }

    public void setCatchmentId(Long catchmentId) {
        this.catchmentId = catchmentId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public String getOperatingIndividualScope() {
        return operatingIndividualScope;
    }

    public void setOperatingIndividualScope(String operatingIndividualScope) {
        this.operatingIndividualScope = operatingIndividualScope;
    }

    public JsonObject getSettings() {
        return settings;
    }

    public void setSettings(JsonObject settings) {
        this.settings = settings;
    }

    public List<Long> getAccountIds() {
        return accountIds == null ? new ArrayList<>() : accountIds;
    }

    public void setAccountIds(List<Long> accountIds) {
        this.accountIds = accountIds;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public JsonObject getSyncSettings() {
        return syncSettings;
    }

    public void setSyncSettings(JsonObject syncSettings) {
        this.syncSettings = syncSettings;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
    }

    public DateTime getLastActivatedDateTime() {
        return lastActivatedDateTime;
    }

    public void setLastActivatedDateTime(DateTime lastActivatedDateTime) {
        this.lastActivatedDateTime = lastActivatedDateTime;
    }

    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public List<Long> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds;
    }

    public List<String> getUserGroupNames() {
        return userGroupNames;
    }

    public void setUserGroupNames(List<String> userGroupNames) {
        this.userGroupNames = userGroupNames;
    }

}
