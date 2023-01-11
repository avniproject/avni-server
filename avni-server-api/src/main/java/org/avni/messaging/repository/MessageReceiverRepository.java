package org.avni.messaging.repository;

import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.ReceiverType;
import org.avni.server.dao.CHSRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageReceiverRepository  extends CHSRepository<MessageReceiver> {

    Optional<MessageReceiver> findByExternalId(String externalId);

    MessageReceiver findByReceiverIdAndReceiverTypeAndExternalId(Long receiverId, ReceiverType receiverType, String externalId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update message_receiver mr set " +
            "is_voided = :isVoided " +
            "where mr.receiver_id = :receiverId", nativeQuery = true)
    void updateVoided(boolean isVoided, Long receiverId);
}
