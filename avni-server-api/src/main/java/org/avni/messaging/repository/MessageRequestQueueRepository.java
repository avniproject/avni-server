package org.avni.messaging.repository;

import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.MessageRule;
import org.avni.server.dao.CHSRepository;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface MessageRequestQueueRepository extends CHSRepository<MessageRequest> {

    Stream<MessageRequest> findAllByDeliveryStatusNotAndIsVoidedFalseAndScheduledDateTimeBetween(MessageDeliveryStatus messageDeliveryStatus, Instant then, Instant now);
    Optional<MessageRequest> findByEntityIdAndMessageRule(Long entityId, MessageRule messageRule);

    default Stream<MessageRequest> findDueMessageRequests(Duration duration) {
        return findAllByDeliveryStatusNotAndIsVoidedFalseAndScheduledDateTimeBetween(MessageDeliveryStatus.Sent,
                DateTimeUtil.toInstant(DateTime.now().minus(duration)), DateTimeUtil.toInstant(DateTime.now()));
    }

    @Modifying(clearAutomatically = true, flushAutomatically=true)
    @Query(value = "update message_request_queue mr set " +
            "is_voided = :isVoided, " +
            "last_modified_date_time = :lastModifiedDateTime, last_modified_by_id = :lastModifiedById " +
            "where mr.entity_id = :entityId", nativeQuery = true)
    void updateVoided(boolean isVoided, Long entityId, Date lastModifiedDateTime, Long lastModifiedById);
    default void updateVoided(boolean isVoided, Long entityId) {
        this.updateVoided(isVoided, entityId, new Date(), UserContextHolder.getUserId());
    }

    Stream<MessageRequest> findAllByDeliveryStatusAndMessageReceiverAndIsVoidedFalse(MessageDeliveryStatus notSent, MessageReceiver messageReceiver);
}
