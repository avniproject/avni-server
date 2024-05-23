package org.avni.server.web.contract;

import org.avni.server.domain.EncounterType;

public class EncounterTypeContract {
    private String name;
    private String uuid;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public static EncounterTypeContract createBasic(EncounterType encounterType) {
        EncounterTypeContract contract = new EncounterTypeContract();
        contract.setName(encounterType.getName());
        contract.setUuid(encounterType.getUuid());
        return contract;
    }
}
