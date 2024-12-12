package org.avni.server.service.builder;

import org.avni.server.dao.ImplementationRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.service.OrganisationService;
import org.avni.server.web.TestWebContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TestOrganisationService {
    private final ImplementationRepository implementationRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final TestWebContextService testWebContextService;
    private final OrganisationService organisationService;
    @Value("${avni.default.org.user.db.password}")
    private String AVNI_DEFAULT_ORG_USER_DB_PASSWORD;

    @Autowired
    public TestOrganisationService(ImplementationRepository implementationRepository, OrganisationRepository organisationRepository, UserRepository userRepository, TestWebContextService testWebContextService, OrganisationService organisationService) {
        this.implementationRepository = implementationRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
        this.testWebContextService = testWebContextService;
        this.organisationService = organisationService;
    }

    public void createOrganisation(Organisation organisation, User adminUser) {
        organisationRepository.save(organisation);
        User orgUser = createUser(organisation, adminUser);
        implementationRepository.createDBUser(organisation.getDbUser(),AVNI_DEFAULT_ORG_USER_DB_PASSWORD);
        testWebContextService.setUser(orgUser);
        organisationService.setupBaseOrganisationMetadata(organisation);
    }

    public User createUser(Organisation organisation, User user) {
        user.setOrganisationId(organisation.getId());
        return userRepository.save(user);
    }
}
