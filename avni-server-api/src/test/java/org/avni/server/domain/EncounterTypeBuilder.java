package org.avni.server.domain;

import static org.junit.Assert.*;

public class EncounterTypeBuilder {
    private EncounterType encounterType = new EncounterType();

    public EncounterTypeBuilder withName(String name) {
        encounterType.setName(name);
    	return this;
    }

    public EncounterType build() {
        return encounterType;
    }
}
