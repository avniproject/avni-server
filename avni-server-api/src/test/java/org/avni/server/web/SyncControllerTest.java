package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
public class SyncControllerTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SyncController syncController;
    @Autowired
    private OrganisationRepository organisationRepository;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CatchmentRepository catchmentRepository;
    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Autowired
    private LocationRepository locationRepository;

    @Test
    public void getSyncDetailsWithScopeAwareEAS() {
        Organisation organisation = organisationRepository.save(new TestOrganisationBuilder().withMandatoryFields().withAccount(accountRepository.getDefaultAccount()).build());
        User user = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().userName("user@example").withAuditUser(userRepository.getDefaultSuperAdmin()).organisationId(organisation.getId()).build());
        setUser(user.getUsername());

        AddressLevelType addressLevelType = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build());
        AddressLevel addressLevel = locationRepository.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        Catchment catchment = catchmentRepository.save(new TestCatchmentBuilder().withDefaultValuesForNewEntity().withAddressLevels(addressLevel).build());
        user = userRepository.save(new UserBuilder(user).withCatchment(catchment).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());
        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        List<EntitySyncStatusContract> contracts = SyncEntityName.getEntitiesWithoutSubEntity().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
    }
}
