package org.avni.messaging.contract.web;

import org.avni.messaging.domain.*;
import org.joda.time.DateTime;

public class GroupMessageRequestResponse {
    private String externalId;
    private MessageDeliveryStatus deliveryStatus;
    private DateTime scheduledDateTime;
    private DateTime deliveredDateTime;
    private String messageTemplateId;

    public static GroupMessageRequestResponse fromMessageRequest(MessageRequest messageRequest) {
        GroupMessageRequestResponse response = new GroupMessageRequestResponse();
        response.deliveryStatus = messageRequest.getDeliveryStatus();
        response.scheduledDateTime = messageRequest.getScheduledDateTime();
        response.deliveredDateTime = messageRequest.getDeliveredDateTime();

        MessageRule messageRule = messageRequest.getMessageRule();
        ManualMessage manualBroadcastMessage = messageRequest.getManualMessage();
        if (messageRule != null) {
            response.messageTemplateId = messageRule.getMessageTemplateId();
        } else if (manualBroadcastMessage != null) {
            response.messageTemplateId = manualBroadcastMessage.getMessageTemplateId();
        }

        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();
        response.externalId = messageReceiver.getExternalId();

        return response;
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

    public DateTime getDeliveredDateTime() {
        return deliveredDateTime;
    }

    public String getMessageTemplateId() {
        return messageTemplateId;
    }
}
