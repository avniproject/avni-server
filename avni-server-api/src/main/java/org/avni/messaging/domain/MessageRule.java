package org.avni.messaging.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.OrganisationAwareEntity;

@Entity
@Table(name = "message_rule")
public class MessageRule extends OrganisationAwareEntity {
    @Column
    @NotNull
    private String name;

    @Column
    private String messageRule;

    @Column
    private String scheduleRule;

    @Column
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column
    private Long entityTypeId;

    @Column
    private String messageTemplateId;

    @Column
    @Enumerated(EnumType.STRING)
    private ReceiverType receiverType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessageRule() {
        return messageRule;
    }

    public void setMessageRule(String messageRule) {
        this.messageRule = messageRule;
    }

    public String getScheduleRule() {
        return scheduleRule;
    }

    public void setScheduleRule(String scheduleRule) {
        this.scheduleRule = scheduleRule;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityTypeId() {
        return entityTypeId;
    }

    public void setEntityTypeId(Long entityTypeId) {
        this.entityTypeId = entityTypeId;
    }

    public String getMessageTemplateId() {
        return messageTemplateId;
    }

    public void setMessageTemplateId(String messageTemplateId) {
        this.messageTemplateId = messageTemplateId;
    }

    public ReceiverType getReceiverType() {
        return receiverType;
    }

    public void setReceiverType(ReceiverType receiverType) {
        this.receiverType = receiverType;
    }
}
