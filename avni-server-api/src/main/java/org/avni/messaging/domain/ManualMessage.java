package org.avni.messaging.domain;

import org.avni.server.domain.OrganisationAwareEntity;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "manual_message")
public class ManualMessage extends OrganisationAwareEntity {
    @Column
    private String messageTemplateId;

    @Column(columnDefinition = "text[]")
    @Type(type = "parameters")
    private String[] parameters;

    @Column
    @Type(type = "nextTriggerDetails")
    private NextTriggerDetails nextTriggerDetails;

    public ManualMessage(String messageTemplateId, String[] parameters) {
        this.messageTemplateId = messageTemplateId;
        this.parameters = parameters;
    }

    public ManualMessage() {
    }

    public String[] getParameters() {
        if(parameters == null) {
            return new String[] {};
        }
        return parameters;
    }

    public void setParameters(String[] parameters) {
        this.parameters = parameters;
    }

    public String getMessageTemplateId() {
        return messageTemplateId;
    }

    public void setMessageTemplateId(String messageTemplateId) {
        this.messageTemplateId = messageTemplateId;
    }

    public NextTriggerDetails getNextTriggerDetails() {
        return nextTriggerDetails;
    }

    public void setNextTriggerDetails(NextTriggerDetails nextTriggerDetails) {
        this.nextTriggerDetails = nextTriggerDetails;
    }
}
