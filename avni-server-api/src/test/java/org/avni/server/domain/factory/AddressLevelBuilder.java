package org.avni.server.domain.factory;

import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;

import java.util.UUID;

public class AddressLevelBuilder {
    private final AddressLevel entity = new AddressLevel();

    public AddressLevel build() {
        return this.entity;
    }

    public AddressLevelBuilder title(String title) {
        entity.setTitle(title);
        return this;
    }

    public AddressLevelBuilder type(AddressLevelType addressLevelType) {
        entity.setType(addressLevelType);
        return this;
    }

    public AddressLevelBuilder id(long id) {
        entity.setId(id);
        return this;
    }

    public AddressLevelBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public AddressLevelBuilder withDefaultValuesForNewEntity() {
        String s = UUID.randomUUID().toString();
        return withUuid(s).title(s);
    }
}
