package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

@Entity
@Table(name = "operational_encounter_type")
@BatchSize(size = 100)
public class OperationalEncounterType extends OrganisationAwareEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_type_id")
    private EncounterType encounterType;

    @Column
    private String name;

    public EncounterType getEncounterType() {
        return encounterType;
    }

    public void setEncounterType(EncounterType encounterType) {
        this.encounterType = encounterType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEncounterTypeUUID() {
        return encounterType.getUuid();
    }

    public Boolean getEncounterTypeVoided() {
        return encounterType.isVoided();
    }

    public String getEncounterTypeName() {
        return encounterType.getName();
    }

    public boolean getActive() {
        return encounterType.getActive();
    }

    @Override
    public DateTime getLastModifiedDateTime() {
        Auditable lastModified = getLastModified(getEncounterType());
        return lastModified.equals(this)?super.getLastModifiedDateTime(): lastModified.getLastModifiedDateTime();
    }

    @Override
    public User getLastModifiedBy() {
        Auditable lastModified = getLastModified(getEncounterType());
        return lastModified.equals(this)?super.getLastModifiedBy(): lastModified.getLastModifiedBy();
    }

    public String getEncounterEligibilityCheckRule() {
        return getEncounterType().getEncounterEligibilityCheckRule();
    }

    public DeclarativeRule getEncounterEligibilityCheckDeclarativeRule() {
        return getEncounterType().getEncounterEligibilityCheckDeclarativeRule();
    }

    public boolean getImmutable(){
        return encounterType.isImmutable();
    }
}
