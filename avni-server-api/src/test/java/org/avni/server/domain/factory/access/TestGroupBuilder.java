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

    public Group build() {
        return entity;
    }
}
