package org.avni.server.framework;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RuleBeanProvider {
    private static ConceptRepository organisationRepository;

    @Autowired
    public RuleBeanProvider(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    public static OrganisationRepository getOrganisationRepository() {
        return organisationRepository;
    }
}
