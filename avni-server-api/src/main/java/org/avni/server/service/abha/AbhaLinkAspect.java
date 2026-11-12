package org.avni.server.service.abha;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.avni.server.domain.Individual;
import org.avni.server.service.OrganisationConfigService;
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

    @Autowired
    public AbhaLinkAspect(OrganisationConfigService organisationConfigService, AbhaLinkService abhaLinkService) {
        this.organisationConfigService = organisationConfigService;
        this.abhaLinkService = abhaLinkService;
    }

    @AfterReturning(value = "@annotation(org.avni.server.common.SubjectIdentitySaved)", returning = "subject")
    public void syncLink(Individual subject) {
        if (subject == null || !organisationConfigService.isAbhaEnabled()) return;
        abhaLinkService.syncLink(subject);
    }
}
