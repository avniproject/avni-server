package org.avni.messaging.service;

import org.avni.messaging.domain.FlowRequest;
import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.ReceiverType;
import org.joda.time.DateTime;
import org.avni.messaging.repository.FlowRequestQueueRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class FlowRequestQueueServiceTest {

    private FlowRequestQueueService flowRequestQueueService;

    @Mock
    private FlowRequestQueueRepository flowRequestQueueRepository;

    @Captor
    ArgumentCaptor<FlowRequest> flowRequestCaptor;

    @Before
    public void setup() {
        initMocks(this);
        flowRequestQueueService = new FlowRequestQueueService(flowRequestQueueRepository);
    }

    @Test
    public void shouldSaveFlowRequestWithCorrectFields() {
        MessageReceiver messageReceiver = new MessageReceiver(ReceiverType.Subject, 1L);
        String flowId = "test-flow-id";

        flowRequestQueueService.saveFlowRequest(messageReceiver, flowId);

        verify(flowRequestQueueRepository).save(flowRequestCaptor.capture());
        FlowRequest savedFlowRequest = flowRequestCaptor.getValue();
        assertThat(savedFlowRequest.getMessageReceiver()).isEqualTo(messageReceiver);
        assertThat(savedFlowRequest.getFlowId()).isEqualTo(flowId);
        assertThat(savedFlowRequest.getRequestDateTime()).isNotNull();
        assertThat(savedFlowRequest.getUuid()).isNotNull();
        assertThat(savedFlowRequest.getDeliveryStatus()).isEqualTo(MessageDeliveryStatus.NotSent);
    }

    @Test
    public void shouldMarkFlowRequestAsFailed() {
        FlowRequest flowRequest = new FlowRequest(new MessageReceiver(ReceiverType.Subject, 1L), "flow-id", DateTime.now());

        flowRequestQueueService.markFailed(flowRequest, MessageDeliveryStatus.Failed);

        verify(flowRequestQueueRepository).save(flowRequestCaptor.capture());
        assertThat(flowRequestCaptor.getValue().getDeliveryStatus()).isEqualTo(MessageDeliveryStatus.Failed);
    }

    @Test
    public void shouldMarkFlowRequestAsComplete() {
        FlowRequest flowRequest = new FlowRequest(new MessageReceiver(ReceiverType.Subject, 1L), "flow-id", DateTime.now());

        flowRequestQueueService.markComplete(flowRequest);

        verify(flowRequestQueueRepository).save(flowRequestCaptor.capture());
        assertThat(flowRequestCaptor.getValue().getDeliveryStatus()).isEqualTo(MessageDeliveryStatus.Sent);
    }
}
