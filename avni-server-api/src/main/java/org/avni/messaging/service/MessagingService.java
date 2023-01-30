package org.avni.messaging.service;

import com.bugsnag.Bugsnag;
import org.avni.messaging.domain.*;
import org.avni.messaging.domain.exception.GlificGroupMessageFailureException;
import org.avni.messaging.repository.GlificMessageRepository;
import org.avni.messaging.repository.ManualBroadcastMessageRepository;
import org.avni.messaging.repository.MessageRequestQueueRepository;
import org.avni.messaging.repository.MessageRuleRepository;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.RuleService;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Service
public class MessagingService {
    private static final Logger logger = LoggerFactory.getLogger(MessagingService.class);
    private final MessageRuleRepository messageRuleRepository;
    private final MessageReceiverService messageReceiverService;
    private final MessageRequestService messageRequestService;
    private GlificMessageRepository glificMessageRepository;
    private final RuleService ruleService;
    private MessageRequestQueueRepository messageRequestQueueRepository;
    private ManualBroadcastMessageRepository manualBroadcastMessageRepository;
    private GroupMessagingService groupMessagingService;
    private Bugsnag bugsnag;

    @Autowired
    public MessagingService(MessageRuleRepository messageRuleRepository, MessageReceiverService messageReceiverService,
                            MessageRequestService messageRequestService, GlificMessageRepository glificMessageRepository,
                            MessageRequestQueueRepository messageRequestQueueRepository,
                            ManualBroadcastMessageRepository manualBroadcastMessageRepository,
                            RuleService ruleService, GroupMessagingService groupMessagingService, Bugsnag bugsnag) {
        this.messageRuleRepository = messageRuleRepository;
        this.messageReceiverService = messageReceiverService;
        this.messageRequestService = messageRequestService;
        this.glificMessageRepository = glificMessageRepository;
        this.messageRequestQueueRepository = messageRequestQueueRepository;
        this.manualBroadcastMessageRepository = manualBroadcastMessageRepository;
        this.ruleService = ruleService;
        this.groupMessagingService = groupMessagingService;
        this.bugsnag = bugsnag;
    }

    public MessageRule find(Long id) {
        return messageRuleRepository.findEntity(id);
    }

    public MessageRule find(String uuid) {
        return messageRuleRepository.findByUuid(uuid);
    }

    public MessageRule saveRule(MessageRule messageRule) {
        return messageRuleRepository.save(messageRule);
    }

    public MessageRule findByIdOrUuid(Long id, String uuid) {
        return uuid != null ? find(uuid) : find(id);
    }

    public Page<MessageRule> findAll(Pageable pageable) {
        return messageRuleRepository.findAll(pageable);
    }

    public List<MessageRule> findAll() {
        return messageRuleRepository.findAll();
    }

    public void onEntitySave(Long entityId, Long entityTypeId, EntityType entityType, Long subjectId, Long userId) {
        List<MessageRule> messageRules = messageRuleRepository.findAllByEntityTypeAndEntityTypeIdAndIsVoidedFalse(entityType, entityTypeId);

        for (MessageRule messageRule : messageRules) {
            MessageReceiver messageReceiver = null;
            if(messageRule.getReceiverType() == ReceiverType.Subject)
                messageReceiver = messageReceiverService.saveReceiverIfRequired(ReceiverType.Subject, subjectId);
            else if(messageRule.getReceiverType() == ReceiverType.User)
                messageReceiver = messageReceiverService.saveReceiverIfRequired(ReceiverType.User, userId);

            DateTime scheduledDateTime = ruleService.executeScheduleRule(messageRule.getEntityType().name(), entityId, messageRule.getScheduleRule());
            messageRequestService.createOrUpdateAutomatedMessageRequest(messageRule, messageReceiver, entityId, scheduledDateTime);
        }
    }

    public void onEntityDelete(Long entityId, EntityType entityType, Long receiverId) {
        messageRequestService.voidMessageRequests(entityId);

        if (entityType.equals(EntityType.Subject)) {
            messageReceiverService.voidMessageReceiver(receiverId);
        }
    }

    @Transactional
    public Page<MessageRule> findByEntityTypeAndEntityTypeId(EntityType entityType, Long entityTypeId, Pageable pageable) {
        return messageRuleRepository.findByEntityTypeAndEntityTypeId(entityType, entityTypeId, pageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MessageRequest sendMessage(MessageRequest messageRequest) {
        logger.debug(String.format("Sending message for %d", messageRequest.getId()));
        try {
            sendMessageToGlific(messageRequest);
            messageRequest = messageRequestService.markComplete(messageRequest);
            logger.debug(String.format("Sent message for %d", messageRequest.getId()));
        } catch (PhoneNumberNotAvailableException p) {
            logger.warn("Phone number not available for receiver: " + messageRequest.getMessageReceiver().getReceiverId());
        } catch (GlificGroupMessageFailureException e) {
            messageRequestService.markPartiallyComplete(messageRequest);
            logger.error("Message sending to all contacts for message request id: " + messageRequest.getId() +
                    "failed with message " + e.getMessage() + ".Will retry again after sometime.");
            bugsnag.notify(e);
        } catch (Exception e) {
            logger.error("Could not send message for message request id: " + messageRequest.getId(), e);
            bugsnag.notify(e);
        }

        return messageRequest;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendMessages() {
        logger.info("Sending messages for organisation " + UserContextHolder.getOrganisation().getName());
        Stream<MessageRequest> requests = messageRequestQueueRepository.findDueMessageRequests();
        requests.forEach(this::sendMessage);
    }

    @Transactional
    public void scheduleBroadcastMessage(String[] groupIds, String messageTemplateId, String[] parameters, DateTime scheduledDateTime) {
        ManualBroadcastMessage manualBroadcastMessage = new ManualBroadcastMessage(messageTemplateId, parameters);
        manualBroadcastMessage.assignUUIDIfRequired();
        manualBroadcastMessageRepository.save(manualBroadcastMessage);

        for (String groupId : groupIds) {
            MessageReceiver messageReceiver = messageReceiverService.saveReceiverIfRequired(ReceiverType.Group, groupId);
            messageRequestService.createManualMessageRequest(manualBroadcastMessage, messageReceiver, scheduledDateTime);
        }
    }

    private void sendMessageToGlific(MessageRequest messageRequest) {
        if(messageRequest.getManualBroadcastMessage() != null)
            groupMessagingService.sendMessageToGroup(messageRequest);
        else
            sendMessageToContact(messageRequest);
    }

    private void sendMessageToContact(MessageRequest messageRequest) {
        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();
        MessageRule messageRule = messageRequest.getMessageRule();
        String[] response = ruleService.executeMessageRule(messageRule.getEntityType().name(), messageRequest.getEntityId(), messageRule.getMessageRule());
        messageReceiverService.ensureExternalIdPresent(messageReceiver);
        glificMessageRepository.sendMessageToContact(messageRule.getMessageTemplateId(), messageReceiver.getExternalId(), response);
    }
}
