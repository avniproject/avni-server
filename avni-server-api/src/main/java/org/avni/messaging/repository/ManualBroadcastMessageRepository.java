package org.avni.messaging.repository;

import org.avni.messaging.domain.ManualBroadcastMessage;
import org.avni.server.dao.CHSRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualBroadcastMessageRepository extends CHSRepository<ManualBroadcastMessage> {
}
