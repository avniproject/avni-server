package org.avni.messaging.api;

import org.avni.messaging.contract.glific.GlificMessageTemplate;
import org.avni.messaging.contract.web.MessageRequestResponse;
import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.domain.MessageRequest;
import org.avni.messaging.domain.ReceiverType;
import org.avni.messaging.service.MessageRequestService;
import org.avni.messaging.service.MessageTemplateService;
import org.avni.server.dao.UserRepository;
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
    private final MessageTemplateService messageTemplateService;

    @Autowired
    public MessageController(MessageRequestService messageRequestService, UserRepository userRepository,
                             IndividualService individualService, MessageTemplateService messageTemplateService) {
        this.messageRequestService = messageRequestService;
        this.userRepository = userRepository;
        this.individualService = individualService;
        this.messageTemplateService = messageTemplateService;
    }

    @RequestMapping(value = MessageEndpoint + "/subject/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsNotYetSentForContactSubject(@PathVariable("id") String subjectId) {
        Stream<MessageRequest> messagesNotSent = messageRequestService.fetchPendingScheduledMessages(
                individualService.getIndividual(subjectId).getId(), ReceiverType.Subject, MessageDeliveryStatus.NotSent);
        List<GlificMessageTemplate> messageTemplates = messageTemplateService.findAll();
        return ResponseEntity.ok(messagesNotSent.map(msg -> MessageRequestResponse.fromMessageRequest(msg, messageTemplates))
                .collect(Collectors.toList()));
    }

    @RequestMapping(value = MessageEndpoint + "/user/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsNotYetSentForContactUser(@PathVariable("id") String userId) {
        Stream<MessageRequest> messagesNotSent = messageRequestService.fetchPendingScheduledMessages(
                userRepository.getUser(userId).getId(), ReceiverType.User, MessageDeliveryStatus.NotSent);
        List<GlificMessageTemplate> messageTemplates = messageTemplateService.findAll();
        return ResponseEntity.ok(messagesNotSent.map(msg -> MessageRequestResponse.fromMessageRequest(msg, messageTemplates))
                .collect(Collectors.toList()));
    }

    @RequestMapping(value = MessageEndpoint + "/contactGroup/{id}/msgsNotYetSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsNotYetSentForContactGroup(@PathVariable("id") String groupId) {
        return getListResponseEntity(groupId, MessageDeliveryStatus.NotSent);
    }

    @RequestMapping(value = MessageEndpoint + "/contactGroup/{id}/msgsSent", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageRequestResponse>> fetchAllMsgsSentForContactGroup(@PathVariable("id") String groupId) {
        return getListResponseEntity(groupId, MessageDeliveryStatus.Sent);
    }

    private ResponseEntity<List<MessageRequestResponse>> getListResponseEntity(String groupId, MessageDeliveryStatus messageDeliveryStatus) {
        Stream<MessageRequest> groupMessages = messageRequestService.getGroupMessages(groupId, messageDeliveryStatus);
        List<GlificMessageTemplate> messageTemplates = messageTemplateService.findAll();
        return ResponseEntity.ok(groupMessages.map(msg -> MessageRequestResponse.fromMessageRequest(msg, messageTemplates))
                .collect(Collectors.toList()));
    }
}
