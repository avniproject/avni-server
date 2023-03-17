package org.avni.messaging.contract.web;

import org.avni.messaging.contract.glific.GlificMessageTemplate;
import org.avni.messaging.domain.*;
import org.joda.time.DateTime;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageRequestResponse {
    private DateTime deliveredDateTime;
    private DateTime scheduledDateTime;
    private String messageTemplateId;
    private String messageRuleParams;
    private GlificMessageTemplate messageTemplate;
    private String createdBy;
    private String lastModifiedBy;

    public static MessageRequestResponse fromMessageRequest(MessageRequest messageRequest, List<GlificMessageTemplate> messageTemplates) {
        MessageRequestResponse response = new MessageRequestResponse();
        response.deliveredDateTime = messageRequest.getDeliveredDateTime();
        response.scheduledDateTime = messageRequest.getScheduledDateTime();

        MessageRule messageRule = messageRequest.getMessageRule();
        ManualMessage manualBroadcastMessage = messageRequest.getManualMessage();

        if (messageRule != null) {
            response.messageTemplateId = messageRule.getMessageTemplateId();
            initializeMessageRuleParams(response, messageRule);
        } else if (manualBroadcastMessage != null) {
            response.messageTemplateId = manualBroadcastMessage.getMessageTemplateId();
            response.messageRuleParams = Arrays.toString(manualBroadcastMessage.getParameters());
        }

        response.messageTemplate = messageTemplates.stream().filter(mt ->response.messageTemplateId.equals(mt.getId()))
                .findFirst().orElse(null);
        response.createdBy = messageRequest.getCreatedByName();
        response.lastModifiedBy = messageRequest.getLastModifiedByName();

        return response;
    }

    private static void initializeMessageRuleParams(MessageRequestResponse response, MessageRule messageRule) {
        String regex = "parameters: \\[(.*)\\]";
        String text = messageRule.getMessageRule();
        Pattern paramPattern = Pattern.compile(regex);
        Matcher matcher = paramPattern.matcher(text);
        boolean found = matcher.find();
        if(found) {
            String[] params = matcher.group(1).replaceAll("'", "").split(",\\s");
            response.messageRuleParams = Arrays.toString(params);
        }
    }

    public DateTime getDeliveredDateTime() {
        return deliveredDateTime;
    }

    public DateTime getScheduledDateTime() {
        return scheduledDateTime;
    }

    public String getMessageTemplateId() {
        return messageTemplateId;
    }

    public String getMessageRuleParams() {
        return messageRuleParams;
    }

    public GlificMessageTemplate getMessageTemplate() {
        return messageTemplate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }
}
