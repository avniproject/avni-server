package org.avni.messaging.api;

import org.avni.messaging.contract.ManualMessageContract;
import org.avni.messaging.service.MessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManualMessageController {
    private final MessagingService messagingService;
    @Autowired
    public ManualMessageController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @RequestMapping(value = "/web/scheduleManualMessage", method = RequestMethod.POST)
    public ResponseEntity.BodyBuilder save(@RequestBody ManualMessageContract manualMessageContract) {
        messagingService.scheduleManualMessage(manualMessageContract.getReceiverId(),
                manualMessageContract.getReceiverType(),
                manualMessageContract.getMessageTemplateId(),
                manualMessageContract.getParameters(),
                manualMessageContract.getScheduledDateTime());
        return ResponseEntity.ok();
    }
}
