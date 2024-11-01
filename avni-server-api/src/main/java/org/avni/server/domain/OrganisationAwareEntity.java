package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class OrganisationAwareEntity extends CHSEntity {
    @Column
    private Long organisationId;

    public Long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(Long organisationId) {
        this.organisationId = organisationId;
    }

    @JsonIgnore
    public Auditable getLastModified(Auditable auditable) {
        return super.getLastModifiedDateTime().isAfter(auditable.getLastModifiedDateTime()) ? this : auditable;
    }

}
