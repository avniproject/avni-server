package org.avni.server.domain.organisation;

import org.avni.server.domain.CHSEntity;

import jakarta.persistence.*;

@Entity
public class OrganisationCategory extends CHSEntity {
    public static final String Production = "Production";
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
