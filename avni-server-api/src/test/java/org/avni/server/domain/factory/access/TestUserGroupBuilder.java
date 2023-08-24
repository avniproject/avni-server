package org.avni.server.domain.factory.access;

import org.avni.server.domain.Group;
import org.avni.server.domain.User;
import org.avni.server.domain.UserGroup;

import java.util.UUID;

public class TestUserGroupBuilder {
    private final UserGroup entity = new UserGroup();

    public TestUserGroupBuilder() {
        withUuid(UUID.randomUUID().toString());
    }

    public TestUserGroupBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public TestUserGroupBuilder withUser(User user) {
        entity.setUser(user);
    	return this;
    }

    public TestUserGroupBuilder withGroup(Group group) {
        entity.setGroup(group);
    	return this;
    }

    public UserGroup build() {
        return entity;
    }
}
