package org.avni.server.domain;

public interface MessageableEntity {
    public Long getEntityTypeId();

    public Long getEntityId();

    public Individual getIndividual();

    public default Object getEntity() {
        return this;
    }

    public User getCreatedBy();

    public boolean isVoided();
}
