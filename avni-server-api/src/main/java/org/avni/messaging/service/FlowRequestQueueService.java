package org.avni.messaging.service;

import org.avni.messaging.domain.FlowRequest;
import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.repository.FlowRequestQueueRepository;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FlowRequestQueueService {

    private final FlowRequestQueueRepository flowRequestQueueRepository;

    @Autowired
    public FlowRequestQueueService(FlowRequestQueueRepository flowRequestQueueRepository) {
        this.flowRequestQueueRepository = flowRequestQueueRepository;
    }

    public FlowRequest saveFlowRequest(MessageReceiver messageReceiver, String flowId) {
        FlowRequest flowRequest = new FlowRequest(messageReceiver, flowId, DateTime.now());
        flowRequest.assignUUIDIfRequired();
        return flowRequestQueueRepository.save(flowRequest);
    }

    public FlowRequest markFailed(FlowRequest flowRequest, MessageDeliveryStatus messageDeliveryStatus) {
        flowRequest.markFailed(messageDeliveryStatus);
        return flowRequestQueueRepository.save(flowRequest);
    }

    public FlowRequest markComplete(FlowRequest flowRequest) {
        flowRequest.markComplete();
        return flowRequestQueueRepository.save(flowRequest);
    }
}
