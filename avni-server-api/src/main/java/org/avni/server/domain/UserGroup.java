package org.avni.server.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Entity
@Table(name = "user_group")
@JsonIgnoreProperties({"user", "group"})
@BatchSize(size = 100)
public class UserGroup extends OrganisationAwareEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Long getGroupId() {
        return group.getId();
    }

    public String getGroupUuid() {
        return group.getUuid();
    }

    public String getGroupName() {
        return group.getName();
    }

    public static UserGroup createMembership(User user, Group group) {
        UserGroup userGroup = new UserGroup();
        userGroup.setGroup(group);
        userGroup.setUser(user);
        userGroup.setUuid(UUID.randomUUID().toString());
        userGroup.setOrganisationId(user.getOrganisationId());
        return userGroup;
    }
}
