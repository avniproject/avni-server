package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserGroupMemberships {

    @JsonProperty("id")
    private final Integer id;

    @JsonProperty("is_group_manager")
    private final boolean isGroupManager;

    public UserGroupMemberships(Integer id, boolean isGroupManager) {
        this.id = id;
        this.isGroupManager = isGroupManager;
    }

    public Integer getId() {
        return id;
    }

    public boolean isGroupManager() {
        return isGroupManager;
    }
}
