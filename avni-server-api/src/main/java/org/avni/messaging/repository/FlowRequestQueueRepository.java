package org.avni.messaging.repository;

import org.avni.messaging.domain.FlowRequest;
import org.avni.server.dao.CHSRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowRequestQueueRepository extends CHSRepository<FlowRequest> {
}
