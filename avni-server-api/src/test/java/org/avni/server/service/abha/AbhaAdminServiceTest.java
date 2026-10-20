package org.avni.server.service.abha;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.CryptoService;
import org.avni.server.web.request.EkaConfigRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AbhaAdminServiceTest {
    private static final String KEY = "W+6wNuSHHVCnO6tqN2EK5CE0kRa+0mcn/fNmDifSkEU=";

    private final OrganisationRepository organisationRepository = mock(OrganisationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExternalSystemConfigRepository externalSystemConfigRepository = mock(ExternalSystemConfigRepository.class);
    private final EkaTokenService ekaTokenService = mock(EkaTokenService.class);
    private AbhaAdminService service;

    @Before
    public void setUp() {
        service = new AbhaAdminService(organisationRepository, userRepository, externalSystemConfigRepository,
                new EkaCredentialCipher(new CryptoService(), KEY), ekaTokenService);

        User admin = new User();
        admin.setId(1L);
        admin.setAdmin(true);
        UserContext userContext = new UserContext();
        userContext.setUser(admin);
        UserContextHolder.create(userContext);

        Organisation organisation = new Organisation("demo");
        organisation.setId(2L);
        organisation.setUuid("org-uuid");
        when(organisationRepository.findByUuidAndAccount_AccountAdmin_User_Id("org-uuid", 1L)).thenReturn(organisation);
        when(externalSystemConfigRepository.findAllBySystemNameAndIsVoidedFalse(any())).thenReturn(Collections.emptyList());
        when(externalSystemConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @After
    public void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        UserContextHolder.clear();
    }

    // A login between the eviction and the commit would read the old settings and cache a token made from them.
    @Test
    public void evictsCachedTokenOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.saveEkaConfig("org-uuid", request());

        verify(ekaTokenService, never()).evict(any());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(ekaTokenService).evict(2L);
    }

    private static EkaConfigRequest request() {
        EkaConfigRequest request = new EkaConfigRequest();
        request.setEnvironment("sandbox");
        request.setClientId("client-1");
        request.setClientSecret("test-secret-value");
        request.setHipIds(Collections.singletonList("TANUH"));
        return request;
    }
}
