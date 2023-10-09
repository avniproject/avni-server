package org.avni.messaging.repository;

import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.ReceiverType;
import org.avni.server.dao.CHSRepository;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
public interface MessageReceiverRepository  extends CHSRepository<MessageReceiver> {

    Optional<MessageReceiver> findByExternalId(String externalId);

    Optional<MessageReceiver> findByReceiverIdAndReceiverType(Long receiverId, ReceiverType receiverType);

    Optional<MessageReceiver> findByReceiverTypeAndExternalId(ReceiverType receiverType, String externalId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update message_receiver mr set " +
            "is_voided = :isVoided, " +
            "last_modified_date_time = :lastModifiedDateTime, last_modified_by_id = :lastModifiedById " +
            "where mr.receiver_id = :receiverId", nativeQuery = true)
    void updateVoided(boolean isVoided, Long receiverId, Date lastModifiedDateTime, Long lastModifiedById);
    default void updateVoided(boolean isVoided, Long receiverId) {
        this.updateVoided(isVoided, receiverId, new Date(), UserContextHolder.getUserId());
    }
}
