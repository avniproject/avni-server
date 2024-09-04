package org.avni.server.service.builder;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.TestOrganisationConfigBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = "prototype")
public class TestOrganisationSetupService {
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private TestOrganisationService testOrganisationService;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganisationCategoryRepository organisationCategoryRepository;
    @Autowired
    private OrganisationStatusRepository organisationStatusRepository;

    private Group group;
    private User user;

    public void setupOrganisation(AbstractControllerIntegrationTest abstractControllerIntegrationTest) {
        group = new TestGroupBuilder().withMandatoryFieldsForNewEntity().build();
        user = new UserBuilder().withDefaultValuesForNewEntity().userName("user@example").withAuditUser(userRepository.getDefaultSuperAdmin()).build();
        Organisation organisation = new TestOrganisationBuilder()
                .withMandatoryFields()
                .setCategory(organisationCategoryRepository.findEntity(1L))
                .withStatus(organisationStatusRepository.findEntity(1L))
                .withAccount(accountRepository.getDefaultAccount()).build();
        testOrganisationService.createOrganisation(organisation, user);
        userRepository.save(new UserBuilder(user).withAuditUser(user).build());
        abstractControllerIntegrationTest.setUser(user.getUsername());

        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        groupRepository.save(group);
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user).build());
    }

    public Group getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }
}
