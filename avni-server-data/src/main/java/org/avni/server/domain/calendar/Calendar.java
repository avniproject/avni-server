package org.avni.server.domain.calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "calendar")
@BatchSize(size = 100)
@JsonIgnoreProperties({"addressLevel"})
public class Calendar extends OrganisationAwareEntity {

    @NotNull
    @Column
    private String name;

    @Column(name = "working_pattern")
    @Type(value = JSONObjectUserType.class)
    private JsonObject workingPattern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_level_id")
    private AddressLevel addressLevel;

    @Column(name = "is_default")
    private boolean isDefault;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JsonObject getWorkingPattern() {
        return workingPattern;
    }

    public void setWorkingPattern(JsonObject workingPattern) {
        this.workingPattern = workingPattern;
    }

    public AddressLevel getAddressLevel() {
        return addressLevel;
    }

    public void setAddressLevel(AddressLevel addressLevel) {
        this.addressLevel = addressLevel;
    }

    public String getAddressLevelUUID() {
        return addressLevel == null ? null : addressLevel.getUuid();
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
