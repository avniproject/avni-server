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

    public AddressLevelTypeBuilder parent(AddressLevelType parent) {
        addressLevelType.setParent(parent);
        return this;
    }

    public AddressLevelTypeBuilder child(AddressLevelType child) {
        addressLevelType.addChildAddressLevelType(child);
        return this;
    }

    public AddressLevelType build() {
        return this.addressLevelType;
    }
}
