package org.avni.server.service.builder;

import org.avni.server.dao.*;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestOrganisationService {
    private final ImplementationRepository implementationRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;

    @Autowired
    public TestOrganisationService(ImplementationRepository implementationRepository, OrganisationRepository organisationRepository, UserRepository userRepository) {
        this.implementationRepository = implementationRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
    }

    public void createOrganisation(Organisation organisation, User adminUser) {
        organisationRepository.save(organisation);
        createUser(organisation, adminUser);
        implementationRepository.createDBUser(organisation);
    }

    public void createUser(Organisation organisation, User user) {
        user.setOrganisationId(organisation.getId());
        userRepository.save(user);
    }
}
