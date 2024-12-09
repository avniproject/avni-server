package org.avni.messaging.domain;

import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "message_request_queue")
public class MessageRequest extends OrganisationAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_rule_id")
    private MessageRule messageRule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_message_id")
    private ManualMessage manualMessage;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_receiver_id")
    private MessageReceiver messageReceiver;

    @Column
    private Long entityId;

    @Column
    @NotNull
    private Instant scheduledDateTime;

    @Column
    @NotNull
    @Enumerated(EnumType.STRING)
    private MessageDeliveryStatus deliveryStatus;

    @Column
    private Instant deliveredDateTime;

    public void setMessageRule(MessageRule messageRule) {
        this.messageRule = messageRule;
    }

    public void setMessageReceiver(MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public void setScheduledDateTime(DateTime scheduledDateTime) {
        this.scheduledDateTime = DateTimeUtil.toInstant(scheduledDateTime);
    }

    public void setDeliveryStatus(MessageDeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public void setDeliveredDateTime(DateTime deliveredDateTime) {
        this.deliveredDateTime = DateTimeUtil.toInstant(deliveredDateTime);
    }

    public MessageRequest() {
    }

    public MessageRequest(MessageRule messageRule, MessageReceiver messageReceiverId, Long entityId, DateTime scheduledDateTime) {
        this.messageRule = messageRule;
        this.messageReceiver = messageReceiverId;
        this.entityId = entityId;
        this.setScheduledDateTime(scheduledDateTime);
        this.deliveryStatus = MessageDeliveryStatus.NotSent;
    }

    public MessageRequest(ManualMessage manualMessage, MessageReceiver messageReceiver, DateTime scheduledDateTime) {
        this.manualMessage = manualMessage;
        this.messageReceiver = messageReceiver;
        this.setScheduledDateTime(scheduledDateTime);
        this.deliveryStatus = MessageDeliveryStatus.NotSent;
    }

    public void markComplete() {
        deliveryStatus = MessageDeliveryStatus.Sent;
        setDeliveredDateTime(DateTime.now());
    }

    public MessageRule getMessageRule() {
        return messageRule;
    }

    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    public Long getEntityId() {
        return entityId;
    }

    public DateTime getScheduledDateTime() {
        return DateTimeUtil.toJodaDateTime(scheduledDateTime);
    }

    public MessageDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public DateTime getDeliveredDateTime() {
        return DateTimeUtil.toJodaDateTime(deliveredDateTime);
    }

    public boolean isDelivered() {
        return getDeliveredDateTime() != null;
    }

    public ManualMessage getManualMessage() {
        return manualMessage;
    }

    public void setManualMessage(ManualMessage manualMessage) {
        this.manualMessage = manualMessage;
    }

    public void markPartiallyComplete() {
        deliveryStatus = MessageDeliveryStatus.PartiallySent;
    }

    public void markFailed(MessageDeliveryStatus messageDeliveryStatus) {
        deliveryStatus = messageDeliveryStatus;
    }
}
