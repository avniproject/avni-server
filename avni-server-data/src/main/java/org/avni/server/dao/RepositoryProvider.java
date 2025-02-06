package org.avni.server.dao;

import org.avni.server.dao.ruleServer.RuleObservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RepositoryProvider {
    private static RuleObservationRepository ruleObservationRepository;
    private static OrganisationRepository organisationRepository;

    @Autowired
    public RepositoryProvider(OrganisationRepository organisationRepository, RuleObservationRepository ruleObservationRepository) {
        RepositoryProvider.organisationRepository = organisationRepository;
        RepositoryProvider.ruleObservationRepository = ruleObservationRepository;
    }

    public static OrganisationRepository getOrganisationRepository() {
        return organisationRepository;
    }

    public static RuleObservationRepository getRuleObservationRepository() {
        return ruleObservationRepository;
    }
}
