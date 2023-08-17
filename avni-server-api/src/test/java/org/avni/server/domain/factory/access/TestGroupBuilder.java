package org.avni.server.domain.factory.access;

import org.avni.server.domain.Group;

import java.util.UUID;

public class TestGroupBuilder {
    private final Group entity = new Group();

    public TestGroupBuilder() {
        withUuid(UUID.randomUUID().toString());
    }

    public TestGroupBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public TestGroupBuilder withName(String name) {
        entity.setName(name);
    	return this;
    }

    public TestGroupBuilder withMandatoryFieldsForNewEntity() {
    	return withName(entity.getUuid());
    }

    public TestGroupBuilder withAllPrivileges(boolean b) {
        entity.setHasAllPrivileges(b);
    	return this;
    }

    public Group build() {
        return entity;
    }
}
