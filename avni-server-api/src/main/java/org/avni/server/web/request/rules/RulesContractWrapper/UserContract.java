package org.avni.server.web.request.rules.RulesContractWrapper;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;

import java.util.List;
import java.util.stream.Collectors;

public class UserContract implements RuleServerEntityContract {
    private String uuid;
    private String name;
    private String username;
    private String organisationName;
    private JsonObject settings;
    private JsonObject syncSettings;
    private List<Long> groupIds;
    private List<String> userGroupNames;

    public static UserContract fromUser(User user, Organisation organisation) {
        UserContract userContract = new UserContract();
        userContract.setUuid(user.getUuid());
        userContract.setName(user.getName());
        userContract.setUsername(user.getUsername());
        userContract.setSettings(user.getSettings());
        userContract.setSyncSettings(user.getSyncSettings());
        userContract.setOrganisationName(organisation.getName());
        userContract.setGroupIds(user.getUserGroups().stream()
                .map(userGroup -> userGroup.getGroupId()).collect(Collectors.toList()));
        userContract.setUserGroupNames(user.getUserGroups().stream()
                .map(userGroup -> userGroup.getGroupName()).collect(Collectors.toList()));
        return userContract;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username.trim() : null;
    }

    public String getOrganisationName() {
        return organisationName;
    }

    public void setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
    }

    public JsonObject getSettings() {
        return settings;
    }

    public void setSettings(JsonObject settings) {
        this.settings = settings;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }

    public JsonObject getSyncSettings() {
        return syncSettings;
    }

    public void setSyncSettings(JsonObject syncSettings) {
        this.syncSettings = syncSettings;
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
