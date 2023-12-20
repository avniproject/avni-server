package org.avni.messaging.service;

import com.bugsnag.Bugsnag;
import org.avni.messaging.domain.*;
import org.avni.messaging.repository.GlificMessageRepository;
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
    private final IndividualService individualService;
    private final UserService userService;
    private final RuleService ruleService;

    @Autowired
    public IndividualMessagingService(MessageReceiverService messageReceiverService,
                                      GlificMessageRepository glificMessageRepository,
                                      IndividualService individualService,
                                      UserService userService, RuleService ruleService,
                                      Bugsnag bugsnag) {
        this.messageReceiverService = messageReceiverService;
        this.glificMessageRepository = glificMessageRepository;
        this.individualService = individualService;
        this.userService = userService;
        this.ruleService = ruleService;
    }

    private void ensureExternalIdPresenceAndSendMessage(MessageReceiver messageReceiver, String templateId, String[] parameters) throws PhoneNumberNotAvailableOrIncorrectException {
        messageReceiverService.ensureExternalIdPresent(messageReceiver);
        glificMessageRepository.sendMessageToContact(templateId, messageReceiver.getExternalId(), parameters);
    }

    public void sendAutomatedMessage(MessageRequest messageRequest) throws PhoneNumberNotAvailableOrIncorrectException, RuleExecutionException {
        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();
        MessageRule messageRule = messageRequest.getMessageRule();
        String entityType = messageRule.getEntityType().name();
        MessageRuleResponseEntity messageRuleResponseEntity = null;
        if(EntityType.isCHSEntityType(messageRule.getEntityType())) {
            messageRuleResponseEntity = ruleService.executeMessageRule(entityType, messageRequest.getEntityId(), messageRule.getMessageRule());
        } else {
            messageRuleResponseEntity = ruleService.executeMessageRuleForEntityTypeUser(messageRequest.getEntityId(), messageRule.getMessageRule());
        }
        ensureExternalIdPresenceAndSendMessage(messageReceiver, messageRule.getMessageTemplateId(), messageRuleResponseEntity.getParameters());
    }

    public void sendManualMessage(MessageRequest messageRequest) throws PhoneNumberNotAvailableOrIncorrectException {
        ManualMessage manualMessage = messageRequest.getManualMessage();
        String[] parameters = manualMessage.getParameters();
        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();
        int[] indicesOfNonStaticParameters = A.findIndicesOf(parameters, NON_STATIC_NAME_PARAMETER);
        if(indicesOfNonStaticParameters.length > 0)
            getUpdatedParameters(messageReceiver, parameters, indicesOfNonStaticParameters );
        ensureExternalIdPresenceAndSendMessage(messageReceiver, manualMessage.getMessageTemplateId(), manualMessage.getParameters());
    }

    private void getUpdatedParameters(MessageReceiver messageReceiver, String[] parameters, int[] indicesOfNonStaticParameters) {
        if(messageReceiver.getReceiverType() == ReceiverType.Subject) {
            Individual individual = individualService.findById(messageReceiver.getReceiverId());
            A.replaceEntriesAtIndicesWith(parameters, indicesOfNonStaticParameters, individual.getFullName());
        }
        else if (messageReceiver.getReceiverType() == ReceiverType.User) {
            Optional<User> user = userService.findById(messageReceiver.getReceiverId());
            A.replaceEntriesAtIndicesWith(parameters, indicesOfNonStaticParameters, user.get().getName());
        }
    }
}

