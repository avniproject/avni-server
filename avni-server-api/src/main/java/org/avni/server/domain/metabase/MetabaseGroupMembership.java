package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetabaseGroupMembership(@JsonProperty("membership_id") Integer membershipId,
                                      @JsonProperty("group_id") Integer groupId,
                                      @JsonProperty("user_id") Integer userId,
                                      @JsonProperty("is_group_manager") boolean isGroupManager)  {

    public MetabaseGroupMembership(Integer membershipId, Integer groupId, Integer userId, boolean isGroupManager) {
        this.membershipId = membershipId;
        this.groupId = groupId;
        this.userId = userId;
        this.isGroupManager = isGroupManager;
    }
}
