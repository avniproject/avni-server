package org.avni.server.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RepositoryProvider {
    private static OrganisationRepository organisationRepository;

    @Autowired
    public RepositoryProvider(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    public static OrganisationRepository getOrganisationRepository() {
        return organisationRepository;
    }
}
