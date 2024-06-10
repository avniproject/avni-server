package org.avni.server.domain.factory;

import org.avni.server.domain.AddressLevelType;

import java.util.UUID;

public class AddressLevelTypeBuilder {
    private final AddressLevelType addressLevelType = new AddressLevelType();

    public AddressLevelTypeBuilder name(String name) {
        addressLevelType.setName(name);
        return this;
    }

    public AddressLevelTypeBuilder level(Double level) {
        addressLevelType.setLevel(level);
        return this;
    }

    public AddressLevelTypeBuilder withUuid(String uuid) {
        addressLevelType.setUuid(uuid);
        return this;
    }

    public AddressLevelTypeBuilder withDefaultValuesForNewEntity() {
        String placeholder = UUID.randomUUID().toString();
        return withUuid(placeholder).name(placeholder).level(3d);
    }

    public AddressLevelType build() {
        return this.addressLevelType;
    }
}
