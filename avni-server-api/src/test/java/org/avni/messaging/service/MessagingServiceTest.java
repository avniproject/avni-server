package org.avni.messaging.service;

import org.avni.messaging.domain.*;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.messaging.repository.GlificMessageRepository;
import org.avni.messaging.repository.ManualMessageRepository;
import org.avni.messaging.repository.MessageRequestQueueRepository;
import org.avni.messaging.repository.MessageRuleRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.RuleExecutionException;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.RuleService;
import org.avni.server.web.request.rules.response.ScheduleRuleResponseEntity;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class MessagingServiceTest {

    private MessagingService messagingService;

    @Mock
    private MessageRuleRepository messageRuleRepository;

    @Mock
    private MessageReceiverService messageReceiverService;

    @Mock
    private MessageRequestService messageRequestService;

    @Mock
    private MessageRequestQueueRepository messageRequestQueueRepository;

    @Mock
    private ManualMessageRepository manualMessageRepository;

    @Mock
    private RuleService ruleService;

    @Mock
    private GlificMessageRepository glificMessageRepository;

    @Mock
    private GroupMessagingService groupMessagingService;

    @Mock
    private IndividualMessagingService individualMessagingService;

    @Captor
    ArgumentCaptor<MessageReceiver> messageReceiver;

    @Captor
    ArgumentCaptor<MessageRequest> messageRequest;

    private String scheduledSinceDays;

    @Before
    public void setup() {
        initMocks(this);
        messagingService = new MessagingService(messageRuleRepository, messageReceiverService,
                messageRequestService, messageRequestQueueRepository,
                manualMessageRepository, ruleService, groupMessagingService, individualMessagingService, null);
        scheduledSinceDays = "4";
    }

    @Test
    public void shouldSaveMessageRequestIfMessageRuleConfiguredOnSaveOfSubjectEntityType() throws RuleExecutionException {
        MessageRule messageRule = mock(MessageRule.class);
        ArrayList<MessageRule> messageRuleList = new ArrayList<MessageRule>() {
            {
                 add(messageRule);
            }
        };

        Long subjectTypeId = 234L;
        Long individualId = 567L;
        Long userId = 890L;
        when(messageRuleRepository.findAllByEntityTypeAndEntityTypeIdAndIsVoidedFalse(EntityType.Subject, subjectTypeId)).thenReturn(messageRuleList);

        MessageReceiver messageReceiver = mock(MessageReceiver.class);
        when(messageReceiverService.saveReceiverIfRequired(ReceiverType.Subject, individualId)).thenReturn(messageReceiver);
        Long messageReceiverId = 890L;
        when(messageReceiver.getId()).thenReturn(messageReceiverId);

        String scheduleRule = "function(params, imports) { return {'scheduledDateTime': '2013-02-04 10:35:24'; }}";
        when(messageRule.getScheduleRule()).thenReturn(scheduleRule);
        Long messageRuleId = 123L;
        when(messageRule.getId()).thenReturn(messageRuleId);
        when(messageRule.getEntityType()).thenReturn(EntityType.Subject);
        when(messageRule.getReceiverType()).thenReturn(ReceiverType.Subject);

        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime scheduledDateTime = formatter.parseDateTime("2013-02-04 10:35:24");
        ScheduleRuleResponseEntity scheduleRuleResponseEntity = new ScheduleRuleResponseEntity();
        scheduleRuleResponseEntity.setScheduledDateTime(scheduledDateTime);
        when(ruleService.executeScheduleRule(messageRule.getEntityType().name(), individualId, scheduleRule)).thenReturn(scheduleRuleResponseEntity);

        messagingService.onEntitySave(individualId, subjectTypeId, EntityType.Subject, individualId, userId);

        verify(messageReceiverService).saveReceiverIfRequired(eq(ReceiverType.Subject), eq(individualId));
        verify(ruleService).executeScheduleRule(eq(messageRule.getEntityType().name()), eq(individualId), eq(scheduleRule));
        verify(messageRequestService).createOrUpdateAutomatedMessageRequest(messageRule, messageReceiver, individualId, scheduledDateTime);
    }

    @Test
    public void shouldSaveMessageRequestsForAllMessageRulesConfigured() throws RuleExecutionException {
        MessageRule messageRule = mock(MessageRule.class);
        MessageRule messageRuleAnother = mock(MessageRule.class);
        ArrayList<MessageRule> messageRuleList = new ArrayList<MessageRule>() {
            {
                 add(messageRule);
                 add(messageRuleAnother);
            }
        };
        Long subjectTypeId = 234L;
        Long individualId = 567L;
        Long userId = 890L;

        when(messageRuleRepository.findAllByEntityTypeAndEntityTypeIdAndIsVoidedFalse(EntityType.Subject, subjectTypeId)).thenReturn(messageRuleList);

        MessageReceiver messageReceiver = mock(MessageReceiver.class);
        when(messageReceiverService.saveReceiverIfRequired(ReceiverType.Subject, individualId)).thenReturn(messageReceiver);
        Long messageReceiverId = 890L;
        when(messageReceiver.getId()).thenReturn(messageReceiverId);

        String scheduleRule = "scheduleRule1";
        when(messageRule.getScheduleRule()).thenReturn(scheduleRule);
        Long messageRuleId = 123L;
        when(messageRule.getId()).thenReturn(messageRuleId);
        when(messageRule.getEntityType()).thenReturn(EntityType.Subject);
        when(messageRule.getReceiverType()).thenReturn(ReceiverType.Subject);

        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime scheduledDateTime = formatter.parseDateTime("2013-02-04 10:35:24");
        ScheduleRuleResponseEntity scheduleRuleResponseEntity = new ScheduleRuleResponseEntity();
        scheduleRuleResponseEntity.setScheduledDateTime(scheduledDateTime);
        when(ruleService.executeScheduleRule(messageRule.getEntityType().name(), individualId, scheduleRule)).thenReturn(scheduleRuleResponseEntity);

        String scheduleRuleAnother = "scheduleRule2";
        when(messageRuleAnother.getScheduleRule()).thenReturn(scheduleRuleAnother);
        Long messageRuleAnotherId = 124L;
        when(messageRuleAnother.getId()).thenReturn(messageRuleAnotherId);
        when(messageRuleAnother.getEntityType()).thenReturn(EntityType.Subject);
        when(messageRuleAnother.getReceiverType()).thenReturn(ReceiverType.Subject);

        DateTime scheduledDateTimeOfAnotherRule = formatter.parseDateTime("2019-02-04 10:35:24");
        ScheduleRuleResponseEntity anotherScheduledRuleResponseEntity = new ScheduleRuleResponseEntity();
        anotherScheduledRuleResponseEntity.setScheduledDateTime(scheduledDateTimeOfAnotherRule);
        when(ruleService.executeScheduleRule(messageRule.getEntityType().name(), individualId, scheduleRuleAnother)).thenReturn(anotherScheduledRuleResponseEntity);

        messagingService.onEntitySave(individualId, subjectTypeId, EntityType.Subject, individualId, userId);

        verify(messageReceiverService, times(2)).saveReceiverIfRequired(eq(ReceiverType.Subject), eq(individualId));
        verify(ruleService).executeScheduleRule(eq(messageRule.getEntityType().name()), eq(individualId), eq(scheduleRule));
        verify(messageRequestService).createOrUpdateAutomatedMessageRequest(messageRule, messageReceiver, individualId, scheduledDateTime);

        verify(ruleService).executeScheduleRule(eq(messageRuleAnother.getEntityType().name()), eq(individualId), eq(scheduleRuleAnother));
        verify(messageRequestService).createOrUpdateAutomatedMessageRequest(messageRuleAnother, messageReceiver, individualId, scheduledDateTimeOfAnotherRule);
    }

    @Test
    public void shouldSendMessagesForAllNotSentMessages() throws RuleExecutionException, PhoneNumberNotAvailableOrIncorrectException, GlificNotConfiguredException {
        MessageRule messageRule = new MessageRule();
        messageRule.setId(10L);
        messageRule.setMessageRule("I am a message rule");
        messageRule.setMessageTemplateId("messageTemplateId");
        messageRule.setEntityType(EntityType.Subject);
        MessageReceiver messageReceiver = new MessageReceiver(ReceiverType.Subject, 1L);
        MessageRequest request = new MessageRequest(messageRule, messageReceiver, 3L, DateTime.now());
        UserContext context = new UserContext();
        context.setOrganisation(new Organisation());
        UserContextHolder.create(context);
        Duration scheduledSince = Duration.standardDays(Long.parseLong(scheduledSinceDays));

        when(messageRequestQueueRepository.findDueMessageRequests(scheduledSince)).thenReturn(Stream.<MessageRequest>builder().add(request).build());
        when(messageRequestService.markComplete(request)).thenReturn(request);

        messagingService.sendMessages(scheduledSince);

        verify(individualMessagingService).sendAutomatedMessage(request);
    }

    @Test
    public void shouldSaveMessageRequestIfMessageRuleConfiguredOnSaveOfUserEntityType() throws RuleExecutionException {
        MessageRule messageRule = mock(MessageRule.class);
        ArrayList<MessageRule> messageRuleList = new ArrayList<MessageRule>() {
            {
                add(messageRule);
            }
        };

        Long userId = 890L;
        when(messageRuleRepository.findAllByReceiverTypeAndEntityTypeAndIsVoidedFalse(ReceiverType.User, EntityType.User)).thenReturn(messageRuleList);

        MessageReceiver messageReceiver = mock(MessageReceiver.class);
        when(messageReceiverService.saveReceiverIfRequired(ReceiverType.User, userId)).thenReturn(messageReceiver);
        Long messageReceiverId = 890L;
        when(messageReceiver.getId()).thenReturn(messageReceiverId);

        String scheduleRule = "function(params, imports) { return {'scheduledDateTime': '2013-02-04 10:35:24'; }}";
        when(messageRule.getScheduleRule()).thenReturn(scheduleRule);
        Long messageRuleId = 123L;
        when(messageRule.getId()).thenReturn(messageRuleId);
        when(messageRule.getEntityType()).thenReturn(EntityType.User);
        when(messageRule.getReceiverType()).thenReturn(ReceiverType.User);

        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime scheduledDateTime = formatter.parseDateTime("2013-02-04 10:35:24");
        ScheduleRuleResponseEntity scheduleRuleResponseEntity = new ScheduleRuleResponseEntity();
        scheduleRuleResponseEntity.setScheduledDateTime(scheduledDateTime);
        when(ruleService.executeScheduleRuleForEntityTypeUser(userId, scheduleRule)).thenReturn(scheduleRuleResponseEntity);

        messagingService.onUserEntitySave(userId, new User());
        verify(messageReceiverService).saveReceiverIfRequired(eq(ReceiverType.User), eq(userId));
        verify(ruleService).executeScheduleRuleForEntityTypeUser(eq(userId), eq(scheduleRule));
        verify(messageRequestService).createOrUpdateAutomatedMessageRequest(messageRule, messageReceiver, userId, scheduledDateTime);
    }
}
