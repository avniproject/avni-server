package org.avni.server.domain.organisation;

import org.avni.server.domain.CHSEntity;

import javax.persistence.Entity;

@Entity
public class OrganisationStatus extends CHSEntity {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
