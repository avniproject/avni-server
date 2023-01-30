package org.avni.messaging.service;

import com.bugsnag.Bugsnag;
import org.avni.messaging.contract.glific.GlificContactGroupContactsResponse;
import org.avni.messaging.domain.ManualBroadcastMessage;
import org.avni.messaging.domain.MessageReceiver;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.NextTriggerDetails;
import org.avni.messaging.domain.exception.GlificGroupMessageFailureException;
import org.avni.messaging.repository.GlificContactRepository;
import org.avni.messaging.repository.GlificMessageRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.service.IndividualService;
import org.avni.server.service.UserService;
import org.avni.server.util.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GroupMessagingService {

    private static final Logger logger = LoggerFactory.getLogger(MessagingService.class);
    public static final String NON_STATIC_NAME_PARAMETER = "@name";
    public static final int CONTACT_MEMBER_PAGE_SIZE = 500;
    private GlificMessageRepository glificMessageRepository;
    private GlificContactRepository glificContactRepository;
    private IndividualService individualService;
    private UserService userService;
    private Bugsnag bugsnag;

    @Autowired
    public GroupMessagingService(GlificMessageRepository glificMessageRepository,
                            GlificContactRepository glificContactRepository, IndividualService individualService,
                                 UserService userService, Bugsnag bugsnag) {
        this.glificMessageRepository = glificMessageRepository;
        this.glificContactRepository = glificContactRepository;
        this.individualService = individualService;
        this.userService = userService;
        this.bugsnag = bugsnag;
    }

    public void sendMessageToGroup(MessageRequest messageRequest) {
        MessageReceiver messageReceiver = messageRequest.getMessageReceiver();
        ManualBroadcastMessage manualBroadcastMessage = messageRequest.getManualBroadcastMessage();
        String[] parameters = manualBroadcastMessage.getParameters();

        int[] indicesOfNonStaticParameters = A.findIndicesOf(parameters, NON_STATIC_NAME_PARAMETER);

        if (indicesOfNonStaticParameters.length > 0)
            sendNonStaticMessageToGroup(messageReceiver, manualBroadcastMessage, parameters, indicesOfNonStaticParameters);
        else
            glificMessageRepository.sendMessageToGroup(messageReceiver.getExternalId(),
                    manualBroadcastMessage.getMessageTemplateId(),
                    manualBroadcastMessage.getParameters());
    }

    private void sendNonStaticMessageToGroup(MessageReceiver messageReceiver, ManualBroadcastMessage manualBroadcastMessage, String[] parameters, int[] indicesOfNonStaticParameters) {
        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts;
        int pageNumber = 0;
        try {
            if (manualBroadcastMessage.getNextTriggerDetails() != null)
                pageNumber = sendMessageToContactsInThePartiallySentPage(messageReceiver,manualBroadcastMessage, parameters, indicesOfNonStaticParameters);

            do {
                PageRequest pageable = PageRequest.of(pageNumber, CONTACT_MEMBER_PAGE_SIZE);
                contactGroupContacts = glificContactRepository.getContactGroupContacts(messageReceiver.getExternalId(),
                        pageable);
                sendNonStaticMessageToContacts(manualBroadcastMessage, parameters, indicesOfNonStaticParameters, contactGroupContacts);
                pageNumber++;
            } while (contactGroupContacts.size() == CONTACT_MEMBER_PAGE_SIZE);
        }
        catch (GlificGroupMessageFailureException exception) {
            manualBroadcastMessage.setNextTriggerDetails(new NextTriggerDetails(pageNumber, exception.getContactIdFailedAt()));
            throw exception;
        }
        catch (Exception exception) {
            manualBroadcastMessage.setNextTriggerDetails(new NextTriggerDetails(pageNumber));
            throw new GlificGroupMessageFailureException(exception.getMessage());
        }
    }

    private int sendMessageToContactsInThePartiallySentPage(MessageReceiver messageReceiver, ManualBroadcastMessage manualBroadcastMessage, String[] parameters, int[] indicesOfNonStaticParameters) {
        int pageNoToResumeFrom = manualBroadcastMessage.getNextTriggerDetails().getPageNo();
        String contactIdToResumeFrom = manualBroadcastMessage.getNextTriggerDetails().getContactId();
        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts;
        List<?> contactGroupIds;

        do {
            PageRequest pageable = PageRequest.of(pageNoToResumeFrom, CONTACT_MEMBER_PAGE_SIZE);
            contactGroupContacts = glificContactRepository.getContactGroupContacts(messageReceiver.getExternalId(),
                    pageable);
            contactGroupIds = contactGroupContacts.stream().map(contactGroupContact -> contactGroupContact.getId()).collect(Collectors.toList());
            pageNoToResumeFrom++;
        } while (!contactGroupIds.contains(contactIdToResumeFrom) && (contactIdToResumeFrom != null));

        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> pendingContactsInThePage = findPendingContactsWith(contactIdToResumeFrom, contactGroupContacts, contactGroupIds);
        sendNonStaticMessageToContacts(manualBroadcastMessage, parameters, indicesOfNonStaticParameters, pendingContactsInThePage);
        return pageNoToResumeFrom;
    }

    private List<GlificContactGroupContactsResponse.GlificContactGroupContacts> findPendingContactsWith(String contactIdToResumeFrom, List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts, List<?> contactGroupIds) {
        if(contactIdToResumeFrom == null) return contactGroupContacts;

        int indexOfContactToResumeFrom = contactGroupIds.indexOf(contactIdToResumeFrom);
        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> pendingContactsInThePage = contactGroupContacts.subList(indexOfContactToResumeFrom, contactGroupContacts.size());
        return pendingContactsInThePage;
    }

    private void sendNonStaticMessageToContacts(ManualBroadcastMessage manualBroadcastMessage, String[] parameters, int[] indicesOfNonStaticParameters, List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts) {
        for (GlificContactGroupContactsResponse.GlificContactGroupContacts contactGroupContact : contactGroupContacts) {
            try {
                Optional<String> name = findNameOfTheContact(contactGroupContact);
                String[] replacedParameters = parameters.clone();
                A.replaceEntriesAtIndicesWith(replacedParameters, indicesOfNonStaticParameters, name.get());
                glificMessageRepository.sendMessageToContact(manualBroadcastMessage.getMessageTemplateId(),
                        contactGroupContact.getId(), replacedParameters);
            }
            catch (Exception exception) {
                throw new GlificGroupMessageFailureException(contactGroupContact.getId(), exception.getMessage());
            }
        }
    }

    private Optional<String> findNameOfTheContact(GlificContactGroupContactsResponse.GlificContactGroupContacts contactGroupContact) {
        Optional<String> name = Stream.<Supplier<Optional<String>>>of(
                        () -> userService.findByPhoneNumber(contactGroupContact.getPhone()).map(User::getName),
                        () -> individualService.findByPhoneNumber(contactGroupContact.getPhone()).map(Individual::getFirstName))
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        return name;
    }
}
