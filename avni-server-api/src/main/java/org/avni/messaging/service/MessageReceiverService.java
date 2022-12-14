package org.avni.messaging.service;

import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.ReceiverType;
import org.avni.messaging.repository.GlificContactRepository;
import org.avni.messaging.repository.MessageReceiverRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.service.IndividualService;
import org.avni.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageReceiverService {

    private final MessageReceiverRepository messageReceiverRepository;

    private final GlificContactRepository glificContactRepository;

    private final IndividualService individualService;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(MessageReceiverService.class);

    @Autowired
    public MessageReceiverService(MessageReceiverRepository messageReceiverRepository, GlificContactRepository glificContactRepository, IndividualService individualService, UserService userService) {
        this.messageReceiverRepository = messageReceiverRepository;
        this.glificContactRepository = glificContactRepository;
        this.individualService = individualService;
        this.userService = userService;
    }

    public MessageReceiver saveReceiverIfRequired(ReceiverType receiverType, Long entityId) {
        MessageReceiver messageReceiver = messageReceiverRepository.findByReceiverIdAndReceiverType(entityId, receiverType);
        if (messageReceiver == null) {
            messageReceiver = new MessageReceiver(receiverType, entityId, null);
            messageReceiver.assignUUIDIfRequired();
            messageReceiverRepository.save(messageReceiver);
        }

        return messageReceiver;
    }

    public MessageReceiver ensureExternalIdPresent(MessageReceiver messageReceiver) {
        if (messageReceiver.getExternalId() != null) {
            return messageReceiver;
        }

        String phoneNumber = null, fullName = null;
        if(messageReceiver.getReceiverType() == ReceiverType.Subject){
            Individual individual = individualService.findById(messageReceiver.getReceiverId());
            phoneNumber = individualService.findPhoneNumber(individual);
            fullName = individual.getFullName();
        }
        else if (messageReceiver.getReceiverType() == ReceiverType.User){
            User user = userService.findById(messageReceiver.getReceiverId()).get();
            phoneNumber = user.getPhoneNumber();
            fullName = user.getName();
        }

        if (phoneNumber == null) {
            throw new PhoneNumberNotAvailableException();
        }

        String externalId = glificContactRepository.getOrCreateGlificContactId(phoneNumber, fullName);
        messageReceiver.setExternalId(externalId);
        return messageReceiverRepository.save(messageReceiver);
    }

    public void voidMessageReceiver(Long receiverId) {
        messageReceiverRepository.updateVoided(true, receiverId);
    }
}
