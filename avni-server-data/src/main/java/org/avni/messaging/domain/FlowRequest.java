package org.avni.messaging.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.OrganisationAwareEntity;
import org.joda.time.DateTime;

@Entity
@Table(name = "flow_request_queue")
public class FlowRequest extends OrganisationAwareEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_receiver_id")
    private MessageReceiver messageReceiver;

    @Column
    @NotNull
    private String flowId;

    @Column
    @NotNull
    private DateTime requestDateTime;

    @Column
    @NotNull
    @Enumerated(EnumType.STRING)
    private MessageDeliveryStatus deliveryStatus = MessageDeliveryStatus.NotSent;

    public FlowRequest() {
    }

    public FlowRequest(MessageReceiver messageReceiver, String flowId, DateTime requestDateTime) {
        this.messageReceiver = messageReceiver;
        this.flowId = flowId;
        this.requestDateTime = requestDateTime;
        this.deliveryStatus = MessageDeliveryStatus.NotSent;
    }

    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    public void setMessageReceiver(MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public DateTime getRequestDateTime() {
        return requestDateTime;
    }

    public void setRequestDateTime(DateTime requestDateTime) {
        this.requestDateTime = requestDateTime;
    }

    public MessageDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(MessageDeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public void markFailed(MessageDeliveryStatus messageDeliveryStatus) {
        this.deliveryStatus = messageDeliveryStatus;
    }

    public void markComplete() {
        this.deliveryStatus = MessageDeliveryStatus.Sent;
    }
}
