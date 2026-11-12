package org.avni.server.service.abha;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.avni.server.domain.Individual;
import org.avni.server.service.OrganisationConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Inside the save's transaction, so a failure fails the save and an upload's duplicate-ABHA check sees earlier rows' links.
@Component
@Aspect
// Outranks @Messageable's hook, so this after-advice runs after it and messaging sees each save unchanged.
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class AbhaLinkAspect {
    private final OrganisationConfigService organisationConfigService;
    private final AbhaLinkService abhaLinkService;
    private final ObjectProvider<AbhaLinkListener> listeners;

    @Autowired
    public AbhaLinkAspect(OrganisationConfigService organisationConfigService, AbhaLinkService abhaLinkService,
                          ObjectProvider<AbhaLinkListener> listeners) {
        this.organisationConfigService = organisationConfigService;
        this.abhaLinkService = abhaLinkService;
        this.listeners = listeners;
    }

    // The listeners (ABDM's requeue of visits waiting for a verified ABHA) hear of the save only with ABHA on, so an
    // organisation with ABHA off pays this one read of its settings for each subject save.
    @AfterReturning(value = "@annotation(org.avni.server.common.SubjectIdentitySaved)", returning = "subject")
    public void syncLink(Individual subject) {
        if (subject == null || !organisationConfigService.isAbhaEnabled()) return;
        boolean changed = abhaLinkService.syncLink(subject);
        listeners.orderedStream().forEach(listener -> listener.abhaLinkKept(subject, changed));
    }
}
