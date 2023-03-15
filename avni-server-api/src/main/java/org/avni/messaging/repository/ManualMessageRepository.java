package org.avni.messaging.repository;

import org.avni.messaging.domain.ManualMessage;
import org.avni.server.dao.CHSRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualMessageRepository extends CHSRepository<ManualMessage> {
}
