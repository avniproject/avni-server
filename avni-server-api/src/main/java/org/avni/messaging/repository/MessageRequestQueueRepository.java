package org.avni.messaging.repository;

import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.MessageRule;
import org.avni.server.dao.CHSRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface MessageRequestQueueRepository extends CHSRepository<MessageRequest> {

    Stream<MessageRequest> findAllByDeliveryStatus(MessageDeliveryStatus messageDeliveryStatus);

    Optional<MessageRequest> findByMessageReceiverAndMessageRule(MessageReceiver messageReceiver, MessageRule messageRule);

    default Stream<MessageRequest> findNotSentMessageRequests() {
        return findAllByDeliveryStatus(MessageDeliveryStatus.NotSent);
    }
}
