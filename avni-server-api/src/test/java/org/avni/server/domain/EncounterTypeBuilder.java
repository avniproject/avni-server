package org.avni.server.domain;

public class EncounterTypeBuilder {
    private EncounterType encounterType = new EncounterType();

    public EncounterTypeBuilder withName(String name) {
        encounterType.setName(name);
        return this;
    }

    public EncounterTypeBuilder withUuid(String uuid) {
        encounterType.setUuid(uuid);
        return this;
    }

    public EncounterType build() {
        return encounterType;
    }
}
