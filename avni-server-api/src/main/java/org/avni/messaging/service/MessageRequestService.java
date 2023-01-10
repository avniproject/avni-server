package org.avni.messaging.service;

import org.avni.messaging.domain.ManualBroadcastMessage;
import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.MessageRule;
import org.avni.messaging.repository.MessageRequestQueueRepository;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageRequestService {
    private final MessageRequestQueueRepository messageRequestRepository;

    @Autowired
    public MessageRequestService(MessageRequestQueueRepository messageRequestRepository) {
        this.messageRequestRepository = messageRequestRepository;
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
}
