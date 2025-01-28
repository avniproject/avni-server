package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateUserGroupRequest {
    @JsonProperty("user_id")
    private final Integer userId;

    @JsonProperty("group_id")
    private final Integer groupId;

    public UpdateUserGroupRequest(Integer userId, Integer groupId) {
        this.userId = userId;
        this.groupId = groupId;
    }

    public Integer getUserId() {
        return userId;
    }

    public Integer getGroupId() {
        return groupId;
    }
}
