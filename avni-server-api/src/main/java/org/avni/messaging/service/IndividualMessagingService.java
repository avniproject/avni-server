package org.avni.messaging.service;

import com.bugsnag.Bugsnag;
import org.avni.messaging.domain.*;
import org.avni.messaging.domain.exception.GlificException;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.messaging.repository.GlificMessageRepository;
import org.avni.messaging.repository.WatiMessageRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.RuleExecutionException;
import org.avni.server.domain.User;
import org.avni.server.service.IndividualService;
import org.avni.server.service.RuleService;
import org.avni.server.service.UserService;
import org.avni.server.util.A;
import org.avni.server.web.request.rules.response.MessageRuleResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IndividualMessagingService {
    public static final String NON_STATIC_NAME_PARAMETER = "@name";
    private final MessageReceiverService messageReceiverService;
    private final GlificMessageRepository glificMessageRepository;
    private final WatiMessageRepository watiMessageRepository;
    private final MessagingProviderResolver messagingProviderResolver;
    private final IndividualService individualService;
    private final UserService userService;
    private final RuleService ruleService;

    @Autowired
    public IndividualMessagingService(MessageReceiverService messageReceiverService,
                                      GlificMessageRepository glificMessageRepository,
                                      WatiMessageRepository watiMessageRepository,
                                      MessagingProviderResolver messagingProviderResolver,
                                      IndividualService individualService,
                                      UserService userService, RuleService ruleService,
                                      Bugsnag bugsnag) {
        this.messageReceiverService = messageReceiverService;
        this.glificMessageRepository = glificMessageRepository;
        this.watiMessageRepository = watiMessageRepository;
        this.messagingProviderResolver = messagingProviderResolver;
        this.individualService = individualService;
        this.userService = userService;
        this.ruleService = ruleService;
    }

    private void ensureExternalIdPresenceAndSendMessage(MessageReceiver messageReceiver, String templateId, String[] parameters) throws PhoneNumberNotAvailableOrIncorrectException, GlificNotConfiguredException {
        messageReceiverService.ensureExternalIdPresent(messageReceiver);
        // Wati: send template message directly using phone number as contact ID.
        // Glific: send HSM message using Glific contact ID.
        if (messagingProviderResolver.isWatiConfigured()) {
            watiMessageRepository.sendTemplateMessage(templateId, messageReceiver.getExternalId(), parameters);
        } else {
            glificMessageRepository.sendMessageToContact(templateId, messageReceiver.getExternalId(), parameters);
        }
    }

    private void ensureExternalIdPresenceAndStartFlow(MessageReceiver messageReceiver, String flowId) throws PhoneNumberNotAvailableOrIncorrectException, GlificNotConfiguredException {
        messageReceiverService.ensureExternalIdPresent(messageReceiver);
        glificMessageRepository.startFlowForContact(flowId, messageReceiver.getExternalId());
    }

    public void sendAutomatedMessage(MessageRequest messageRequest) throws PhoneNumberNotAvailableOrIncorrectException, RuleExecutionException, GlificNotConfiguredException {
        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();
        MessageRule messageRule = messageRequest.getMessageRule();
        String entityType = messageRule.getEntityType().name();
        MessageRuleResponseEntity messageRuleResponseEntity = null;
        if (EntityType.isCHSEntityType(messageRule.getEntityType())) {
            messageRuleResponseEntity = ruleService.executeMessageRule(entityType, messageRequest.getEntityId(), messageRule.getMessageRule());
        } else {
            messageRuleResponseEntity = ruleService.executeMessageRuleForEntityTypeUser(messageRequest.getEntityId(), messageRule.getMessageRule());
        }
        ensureExternalIdPresenceAndSendMessage(messageReceiver, messageRule.getMessageTemplateId(), messageRuleResponseEntity.getParameters());
    }

    public void sendManualMessage(MessageRequest messageRequest) throws PhoneNumberNotAvailableOrIncorrectException, GlificNotConfiguredException {
        ManualMessage manualMessage = messageRequest.getManualMessage();
        String[] parameters = manualMessage.getParameters();
        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();
        int[] indicesOfNonStaticParameters = A.findIndicesOf(parameters, NON_STATIC_NAME_PARAMETER);
        if (indicesOfNonStaticParameters.length > 0)
            getUpdatedParameters(messageReceiver, parameters, indicesOfNonStaticParameters);
        ensureExternalIdPresenceAndSendMessage(messageReceiver, manualMessage.getMessageTemplateId(), manualMessage.getParameters());
    }

    public void invokeStartFlowForContact(MessageReceiver messageReceiver, String flowId) throws PhoneNumberNotAvailableOrIncorrectException, GlificNotConfiguredException, GlificException {
        if (messageReceiver.getReceiverType().equals(ReceiverType.Group)) {
            throw new GlificException("Invocation of Start Flow not allowed for Group");
        }
        ensureExternalIdPresenceAndStartFlow(messageReceiver, flowId);
    }


    private void getUpdatedParameters(MessageReceiver messageReceiver, String[] parameters, int[] indicesOfNonStaticParameters) {
        if (messageReceiver.getReceiverType() == ReceiverType.Subject) {
            Individual individual = individualService.findById(messageReceiver.getReceiverId());
            A.replaceEntriesAtIndicesWith(parameters, indicesOfNonStaticParameters, individual.getFullName());
        } else if (messageReceiver.getReceiverType() == ReceiverType.User) {
            Optional<User> user = userService.findById(messageReceiver.getReceiverId());
            A.replaceEntriesAtIndicesWith(parameters, indicesOfNonStaticParameters, user.get().getName());
        }
    }
}

