package org.avni.messaging.contract;

import org.joda.time.DateTime;

public class ManualBroadcastMessageContract {
    private String[] groupIds;
    private String messageTemplateId;
    private String[] parameters;
    private DateTime scheduledDateTime;

    public String[] getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(String[] groupIds) {
        this.groupIds = groupIds;
    }

    public String getMessageTemplateId() {
        return messageTemplateId;
    }

    public void setMessageTemplateId(String messageTemplateId) {
        this.messageTemplateId = messageTemplateId;
    }

    public String[] getParameters() {
        return parameters;
    }

    public void setParameters(String[] parameters) {
        this.parameters = parameters;
    }

    public DateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(DateTime scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
    }
}
