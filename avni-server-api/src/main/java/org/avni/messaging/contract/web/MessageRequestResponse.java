package org.avni.messaging.contract.web;

import org.avni.messaging.domain.*;
import org.joda.time.DateTime;

public class MessageRequestResponse {
    private Long entityTypeId;
    private EntityType entityType;
    private Long receiverId;
    private ReceiverType receiverType;
    private String externalId;
    private MessageDeliveryStatus deliveryStatus;
    private DateTime scheduledDateTime;
    private String messageTemplateName;
    private String messageTemplateId;

    public static MessageRequestResponse fromMessageRequest(MessageRequest messageRequest) {
        MessageRequestResponse response = new MessageRequestResponse();
        MessageRule messageRule = messageRequest.getMessageRule();
        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();

        response.entityTypeId = messageRule.getEntityTypeId();
        response.entityType = messageRule.getEntityType();
        response.receiverId = messageReceiver.getReceiverId();
        response.receiverType = messageReceiver.getReceiverType();
        response.externalId = messageReceiver.getExternalId();
        response.deliveryStatus = messageRequest.getDeliveryStatus();
        response.scheduledDateTime = messageRequest.getScheduledDateTime();
        response.messageTemplateName = messageRule.getName();
        response.messageTemplateId = messageRule.getMessageTemplateId();
        return response;
    }

    public Long getEntityTypeId() {
        return entityTypeId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public ReceiverType getReceiverType() {
        return receiverType;
    }

    public String getExternalId() {
        return externalId;
    }

    public MessageDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public DateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public String getMessageTemplateName() {
        return messageTemplateName;
    }

    public String getMessageTemplateId() {
        return messageTemplateId;
    }
}
