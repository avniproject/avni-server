package org.avni.messaging.contract;

import org.avni.messaging.domain.EntityType;
import org.avni.messaging.domain.MessageRule;
import org.avni.messaging.domain.ReceiverType;
import org.avni.server.domain.CHSEntity;
import org.avni.server.service.EntityTypeRetrieverService;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

public class MessageRuleContract {
    public MessageRuleContract() {
    }

    public MessageRuleContract(MessageRule messageRule, EntityTypeRetrieverService entityTypeRetrieverService) {
        if(entityTypeRetrieverService != null && EntityType.isCHSEntityType(messageRule.getEntityType())) {
            CHSEntity entity = entityTypeRetrieverService.getEntityType(messageRule.getEntityType().toString(), messageRule.getEntityTypeId());
            setEntityTypeUuid(entity.getUuid());
        }

        BeanUtils.copyProperties(messageRule, this);
        setEntityType(messageRule.getEntityType().toString());
        setReceiverType(messageRule.getReceiverType().toString());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Boolean getVoided() {
        return isVoided;
    }

    public void setVoided(Boolean voided) {
        isVoided = voided;
    }

    private Long id;

    private String uuid;

    private String name;
    private String messageRule;
    private String scheduleRule;
    private String entityType;
    private Long entityTypeId;
    private String entityTypeUuid;
    private String messageTemplateId;
    private String receiverType;
    private Boolean isVoided;

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

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityTypeId() {
        return entityTypeId;
    }

    public void setEntityTypeId(Long entityTypeId) {
        this.entityTypeId = entityTypeId;
    }

    public String getEntityTypeUuid() {
        return entityTypeUuid;
    }

    public void setEntityTypeUuid(String entityTypeUuid) {
        this.entityTypeUuid = entityTypeUuid;
    }

    public String getMessageTemplateId() {
        return messageTemplateId;
    }

    public void setMessageTemplateId(String messageTemplateId) {
        this.messageTemplateId = messageTemplateId;
    }

    public String getReceiverType() {
        return receiverType;
    }

    public void setReceiverType(String receiverType) {
        this.receiverType = receiverType;
    }

    public Boolean getIsVoided() {
        return isVoided;
    }

    public void setIsVoided(Boolean isVoided) {
        this.isVoided = isVoided;
    }

    public static MessageRule toModel(MessageRuleContract messageRuleContract, MessageRule messageRule, EntityTypeRetrieverService entityTypeRetrieverService) {
        if(messageRule == null) {
            messageRule = new MessageRule();
        }

        messageRule.setName(messageRuleContract.getName());
        messageRule.setUuid(messageRuleContract.getUuid());
        String entityTypeString = StringUtils.capitalize(messageRuleContract.getEntityType());
        EntityType entityType = EntityType.valueOf(entityTypeString);
        Long entityTypeId = -1l;
        if(EntityType.isCHSEntityType(entityType)) {
            entityTypeId = entityTypeRetrieverService.getEntityType(entityTypeString, messageRuleContract.getEntityTypeUuid()).getId();
        }
        messageRule.setEntityTypeId(entityTypeId);
        messageRule.setEntityType(entityType);
        messageRule.setScheduleRule(messageRuleContract.getScheduleRule());
        messageRule.setMessageRule(messageRuleContract.getMessageRule());
        messageRule.setReceiverType(ReceiverType.valueOf(StringUtils.capitalize(messageRuleContract.getReceiverType())));
        messageRule.setMessageTemplateId(messageRuleContract.getMessageTemplateId());
        messageRule.setVoided(messageRuleContract.getVoided());
        return messageRule;
    }
}
