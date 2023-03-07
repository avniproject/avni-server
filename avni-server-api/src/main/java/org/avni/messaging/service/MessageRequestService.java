package org.avni.messaging.service;

import org.avni.messaging.domain.*;
import org.avni.messaging.domain.exception.MessageReceiverNotFoundError;
import org.avni.messaging.repository.MessageRequestQueueRepository;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.stream.Stream;

@Service
public class MessageRequestService {
    private final MessageRequestQueueRepository messageRequestRepository;
    private final MessageReceiverService messageReceiverService;

    @Autowired
    public MessageRequestService(MessageRequestQueueRepository messageRequestRepository, MessageReceiverService messageReceiverService) {
        this.messageRequestRepository = messageRequestRepository;
        this.messageReceiverService = messageReceiverService;
    }

    public MessageRequest createOrUpdateAutomatedMessageRequest(MessageRule messageRule, MessageReceiver messageReceiver, Long entityId, DateTime scheduledDateTime) {
        MessageRequest messageRequest = messageRequestRepository.findByEntityIdAndMessageRule(entityId, messageRule)
                .orElse(new MessageRequest(messageRule, messageReceiver, entityId, scheduledDateTime));
        if (messageRequest.isDelivered()) {
            return messageRequest;
        }
        messageRequest.setScheduledDateTime(scheduledDateTime);
        messageRequest.assignUUIDIfRequired();
        return messageRequestRepository.save(messageRequest);
    }

    public MessageRequest createManualMessageRequest(ManualBroadcastMessage manualBroadcastMessage, MessageReceiver messageReceiver, DateTime scheduledDateTime) {
        MessageRequest messageRequest = new MessageRequest(manualBroadcastMessage, messageReceiver, scheduledDateTime);
        messageRequest.assignUUIDIfRequired();
        return messageRequestRepository.save(messageRequest);
    }

    public MessageRequest markComplete(MessageRequest messageRequest) {
        messageRequest.markComplete();
        return messageRequestRepository.save(messageRequest);
    }

    public void voidMessageRequests(Long entityId) {
        messageRequestRepository.updateVoided(true, entityId);
    }

    public void markPartiallyComplete(MessageRequest messageRequest) {
        messageRequest.markPartiallyComplete();
        messageRequestRepository.save(messageRequest);
    }

    public Stream<MessageRequest> fetchPendingScheduledMessages(Long receiverId, ReceiverType receiverType, MessageDeliveryStatus messageDeliveryStatus) {
        return messageReceiverService.findMessageReceiver(receiverId, receiverType).map(messageReceiver ->
                messageRequestRepository.findAllByDeliveryStatusAndMessageReceiverAndIsVoidedFalse(messageDeliveryStatus, messageReceiver)
        ).orElseThrow(MessageReceiverNotFoundError::new);
    }

    public MessageRequest markFailed(MessageRequest messageRequest, MessageDeliveryStatus messageDeliveryStatus) {
        messageRequest.markFailed(messageDeliveryStatus);
        return messageRequestRepository.save(messageRequest);
    }

    public Stream<MessageRequest> getGroupMessages(String groupId, MessageDeliveryStatus messageDeliveryStatus) {
        return messageReceiverService.findExternalMessageReceiver(groupId).map(messageReceiver ->
                messageRequestRepository.findAllByDeliveryStatusAndMessageReceiverAndIsVoidedFalse(messageDeliveryStatus, messageReceiver)
        ).orElseThrow(MessageReceiverNotFoundError::new);
    }
}
