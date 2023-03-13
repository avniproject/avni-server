package org.avni.messaging.api;

import org.avni.messaging.contract.ManualBroadcastMessageContract;
import org.avni.messaging.service.MessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManualBroadcastMessageController {
    private final MessagingService messagingService;
    @Autowired
    public ManualBroadcastMessageController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @RequestMapping(value = "/web/scheduleManualBroadcastMessage", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity.BodyBuilder save(@RequestBody ManualBroadcastMessageContract manualBroadcastMessageContract) {
        messagingService.scheduleBroadcastMessage(manualBroadcastMessageContract.getReceiverIds(),
                manualBroadcastMessageContract.getReceiverType(),
                manualBroadcastMessageContract.getMessageTemplateId(),
                manualBroadcastMessageContract.getParameters(),
                manualBroadcastMessageContract.getScheduledDateTime());
        return ResponseEntity.ok();
    }
}
