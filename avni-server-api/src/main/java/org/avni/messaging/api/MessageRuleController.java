package org.avni.messaging.api;

import org.avni.messaging.contract.MessageRuleWebContract;
import org.avni.messaging.domain.EntityType;
import org.avni.messaging.domain.MessageRule;
import org.avni.messaging.service.MessagingService;
import org.avni.server.service.EntityTypeRetrieverService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
public class MessageRuleController {

    private final MessagingService messagingService;
    private final AccessControlService accessControlService;
    private final EntityTypeRetrieverService entityTypeRetrieverService;

    @Autowired
    public MessageRuleController(MessagingService messagingService, AccessControlService accessControlService,
                                 EntityTypeRetrieverService entityTypeRetrieverService) {
        this.messagingService = messagingService;
        this.accessControlService = accessControlService;
        this.entityTypeRetrieverService = entityTypeRetrieverService;
    }

    @RequestMapping(value = "/web/messageRule", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<MessageRuleWebContract> save(@RequestBody MessageRuleWebContract messageRuleContract) {
        accessControlService.checkPrivilege(entityTypeRetrieverService.findPrivilegeType(StringUtils
                .capitalize(messageRuleContract.getEntityType())));

        MessageRule existingEntity = messagingService.findByIdOrUuid(messageRuleContract.getId(), messageRuleContract.getUuid());
        MessageRule messageRule = messageRuleContract.toModel(existingEntity);

        messageRule = messagingService.saveRule(messageRule);
        return ResponseEntity.ok(new MessageRuleWebContract(messageRule, null));
    }

    /**
     * Find all messageRules
     * Either use no parameters, or both entityType and entityTypeId together.
     * EntityType should be one of <code>EntityType</code>
     *
     * @param entityType
     * @param entityTypeId
     * @param pageable
     * @return
     */
    @RequestMapping(value = "/web/messageRule", method = RequestMethod.GET)
    @Transactional
    public Page<MessageRuleWebContract> find(@RequestParam(required = false) String entityType, @RequestParam (required = false) Long entityTypeId, Pageable pageable) {
        if (isAString(entityType) && entityTypeId != null) {
            EntityType entityTypeValue = EntityType.valueOf(StringUtils.capitalize(entityType));
            return messagingService.findByEntityTypeAndEntityTypeId(entityTypeValue, entityTypeId, pageable).map(messageRule -> new MessageRuleWebContract(messageRule, null));
        }

        return messagingService.findAll(pageable).map(messageRule -> new MessageRuleWebContract(messageRule, null));
    }

    @RequestMapping(value = "/web/messageRule/{id}", method = RequestMethod.GET)
    @Transactional
    public ResponseEntity<MessageRuleWebContract> findOne(@PathVariable("id") Long id ) {
        MessageRule messageRule = messagingService.find(id);
        return messageRule == null? ResponseEntity.notFound().build() : ResponseEntity.ok(new MessageRuleWebContract(messageRule, null));
    }

    private boolean isAString(String s) {
        return !StringUtils.isEmpty(s);
    }
}
