package org.avni.messaging.api;

import org.avni.messaging.contract.glific.GlificContactResponse;
import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.repository.GlificContactRepository;
import org.avni.messaging.service.MessagingService;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.avni.server.service.IndividualService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class MessageController {

    private final GlificContactRepository glificContactRepository;
    private final IndividualService individualService;
    private final MessagingService messagingService;
    private final UserRepository userRepository;

    @Autowired
    public MessageController(GlificContactRepository glificContactRepository, IndividualService individualService,
                             MessagingService messagingService, UserRepository userRepository) {
        this.glificContactRepository = glificContactRepository;
        this.individualService = individualService;
        this.messagingService = messagingService;
        this.userRepository = userRepository;
    }

    @RequestMapping(value = "/web/message/subject/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequest>> fetchAllMsgsNotYetSentForContactSubject(@PathVariable("id") long subjectId) {
        String phoneNumber = individualService.fetchIndividualPhoneNumber(subjectId);
        GlificContactResponse glificContactResponse = glificContactRepository.findContact(phoneNumber);
        Stream<MessageRequest> messagesNotSent = messagingService.fetchPendingScheduledMessages(glificContactResponse, MessageDeliveryStatus.NotSent);
        return ResponseEntity.ok(messagesNotSent.collect(Collectors.toList()));
    }

    @RequestMapping(value = "/web/message/user/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequest>> fetchAllMsgsNotYetSentForContactUser(@PathVariable("id") long userId) {
        User user = userRepository.findById(userId).orElseThrow(EntityNotFoundException::new);
        GlificContactResponse glificContactResponse = glificContactRepository.findContact(user.getPhoneNumber());
        Stream<MessageRequest> messagesNotSent = messagingService.fetchPendingScheduledMessages(glificContactResponse, MessageDeliveryStatus.NotSent);
        return ResponseEntity.ok(messagesNotSent.collect(Collectors.toList()));
    }
}
