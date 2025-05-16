package org.avni.messaging.contract;

import org.avni.messaging.domain.EntityType;
import org.avni.messaging.domain.MessageRule;
import org.avni.messaging.domain.ReceiverType;
import org.avni.server.domain.CHSEntity;
import org.avni.server.service.EntityTypeRetrieverService;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.util.Optional;

public class MessageRuleWebContract extends MessageRuleContract {
    private UserContract createdBy;
    private DateTime createdDateTime;
    private UserContract lastModifiedBy;
    private DateTime lastModifiedDateTime;

    public MessageRuleWebContract() {
    }

    public MessageRuleWebContract(MessageRule messageRule, EntityTypeRetrieverService entityTypeRetrieverService) {
        if(entityTypeRetrieverService != null && EntityType.isCHSEntityType(messageRule.getEntityType())) {
            CHSEntity entity = entityTypeRetrieverService.getEntityType(messageRule.getEntityType().toString(), messageRule.getEntityTypeId());
            setEntityTypeUuid(entity.getUuid());
        }

        BeanUtils.copyProperties(messageRule, this);
        setEntityType(messageRule.getEntityType().toString());
        setReceiverType(messageRule.getReceiverType().toString());
        setCreatedBy(new UserContract(messageRule.getCreatedBy()));
        setLastModifiedBy(new UserContract(messageRule.getLastModifiedBy()));
    }

    public MessageRule toModel(MessageRule messageRule) {
        MessageRule result = messageRule;
        if (result == null) {
            result = new MessageRule();
            result.assignUUID();
        } else {
            result.setId(getId());
            result.setUuid(getUuid());
        }
        result.setName(getName());
        result.setMessageRule(getMessageRule());
        result.setScheduleRule(getScheduleRule());
        if (getEntityType() != null) {
            result.setEntityType(EntityType.valueOf(StringUtils.capitalize(getEntityType())));
        }
        if (getReceiverType() != null) {
            result.setReceiverType(ReceiverType.valueOf(StringUtils.capitalize(getReceiverType())));
        }
        result.setEntityTypeId(getEntityTypeId());
        result.setMessageTemplateId(getMessageTemplateId());
        result.setVoided(Optional.ofNullable(getVoided()).orElse(false));

        return result;
    }

    public UserContract getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserContract createdBy) {
        this.createdBy = createdBy;
    }

    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public UserContract getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(UserContract lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
    }

}
