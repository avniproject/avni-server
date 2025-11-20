package org.avni.server.domain.organisation;

import jakarta.persistence.Entity;
import org.avni.server.domain.CHSEntity;

@Entity
public class OrganisationCategory extends CHSEntity {
    public static final String Production = "Production";
    public static final String UAT = "UAT";
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
