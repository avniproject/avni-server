package org.avni.server.common;

import com.bugsnag.Bugsnag;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.avni.messaging.domain.EntityType;
import org.avni.messaging.service.MessagingService;
import org.avni.server.domain.MessageableEntity;
import org.avni.server.domain.RuleExecutionException;
import org.avni.server.service.OrganisationConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class MessageableAnnotationAspect {

    private MessagingService messagingService;

    private OrganisationConfigService organisationConfigService;

    private Bugsnag bugsnag;
    private static Logger logger = LoggerFactory.getLogger(MessageableAnnotationAspect.class);

    @Autowired
    public MessageableAnnotationAspect(MessagingService messagingService, OrganisationConfigService organisationConfigService, Bugsnag bugsnag) {
        this.messagingService = messagingService;
        this.organisationConfigService = organisationConfigService;
        this.bugsnag = bugsnag;
    }

    @AfterReturning(value = "@annotation(org.avni.server.common.Messageable)", returning = "entity")
    public MessageableEntity sendMessage(JoinPoint joinPoint, MessageableEntity entity) {
        logger.info("MessageableAnnotationAspect invoked.");

        if (!organisationConfigService.isMessagingEnabled())
            return entity;

        try {
            EntityType entityType = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Messageable.class).value();

            if (EntityType.isCHSEntityType(entityType)) {
                handleCHSEntityTypeInvocations(entity, entityType);
            }

            if (entity != null /* entity is null when we attempt to create a pre-existing user during bulk upload*/
                    && EntityType.User.equals(entityType)) {
                handleUserEntityTypeInvocations(entity, entityType);
            }

            return entity;
        } catch (Exception e) {
            bugsnag.notify(e);
            logger.error("Could not save/delete message request for entity " + entity.getEntityId() + " with type id " + entity.getEntityTypeId(), e);
            return entity;
        }
    }

    private void handleUserEntityTypeInvocations(MessageableEntity entity, EntityType entityType) throws RuleExecutionException {
        messagingService.onUserEntitySave(entity.getEntityId(), entity.getCreatedBy());
    }


    private void handleCHSEntityTypeInvocations(MessageableEntity entity, EntityType entityType) throws RuleExecutionException {
        if (entity.isVoided()) {
            messagingService.onEntityDelete(entity.getEntityId(), entityType, entity.getIndividual().getId());
        } else {
            messagingService.onEntitySave(entity.getEntityId(), entity.getEntityTypeId(), entityType, entity.getIndividual().getId(), entity.getCreatedBy().getId());
        }
    }
}
