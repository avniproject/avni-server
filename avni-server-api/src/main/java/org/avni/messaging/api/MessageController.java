package org.avni.messaging.api;

import org.avni.messaging.contract.web.GroupMessageRequestResponse;
import org.avni.messaging.contract.web.MessageRequestResponse;
import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.ReceiverType;
import org.avni.messaging.service.MessageRequestService;
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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class MessageController {
    private static final String MessageEndpoint = "/web/message";
    private final MessageRequestService messageRequestService;
    private final UserRepository userRepository;
    private final IndividualService individualService;

    @Autowired
    public MessageController(MessageRequestService messageRequestService, UserRepository userRepository,
                             IndividualService individualService) {
        this.messageRequestService = messageRequestService;
        this.userRepository = userRepository;
        this.individualService = individualService;
    }

    @RequestMapping(value = MessageEndpoint + "/subject/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsNotYetSentForContactSubject(@PathVariable("id") String subjectId) {
        Stream<MessageRequest> messagesNotSent = messageRequestService.fetchPendingScheduledMessages(
                individualService.getIndividual(subjectId).getId(), ReceiverType.Subject, MessageDeliveryStatus.NotSent);
        return ResponseEntity.ok(messagesNotSent.map(MessageRequestResponse::fromMessageRequest).collect(Collectors.toList()));
    }

    @RequestMapping(value = MessageEndpoint + "/user/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequest>> fetchAllMsgsNotYetSentForContactUser(@PathVariable("id") String userId) {
        User user = userRepository.getUser(userId);
        Stream<MessageRequest> messagesNotSent = messageRequestService.fetchPendingScheduledMessages(user.getId(),
                ReceiverType.User, MessageDeliveryStatus.NotSent);
        return ResponseEntity.ok(messagesNotSent.collect(Collectors.toList()));
    }

    @RequestMapping(value = MessageEndpoint + "/contactGroup/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<GroupMessageRequestResponse>> fetchAllMsgsNotYetSentForContactGroup(@PathVariable("id") String groupId) {
        Stream<MessageRequest> messagesNotSent = messageRequestService.getGroupMessages(groupId, MessageDeliveryStatus.NotSent);
        return ResponseEntity.ok(messagesNotSent.map(GroupMessageRequestResponse::fromMessageRequest).collect(Collectors.toList()));
    }

    @RequestMapping(value = MessageEndpoint + "/contactGroup/{id}/msgsSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<GroupMessageRequestResponse>> fetchAllMsgsSentForContactGroup(@PathVariable("id") String groupId) {
        Stream<MessageRequest> messagesNotSent = messageRequestService.getGroupMessages(groupId, MessageDeliveryStatus.Sent);
        return ResponseEntity.ok(messagesNotSent.map(GroupMessageRequestResponse::fromMessageRequest).collect(Collectors.toList()));
    }
}
