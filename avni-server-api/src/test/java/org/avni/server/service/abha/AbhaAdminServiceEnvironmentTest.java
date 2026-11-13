package org.avni.server.service.abha;

import jakarta.persistence.EntityManager;
import org.avni.server.dao.AccountAdminRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.EkaConfigRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// A server allowing production only refuses Eka settings for the sandbox before anything is stored. The rest of the save
// is AbhaAdminControllerIntegrationTest's, where the test settings allow both.
public class AbhaAdminServiceEnvironmentTest {
    private final OrganisationRepository organisationRepository = mock(OrganisationRepository.class);
    private final ExternalSystemConfigRepository configRepository = mock(ExternalSystemConfigRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AbhaAdminService service = new AbhaAdminService(entityManager, organisationRepository, mock(UserRepository.class),
            configRepository, mock(EkaCredentialCipher.class), mock(AccountAdminRepository.class), new EkaEnvironments("production"));

    @Before
    public void actAsSuperAdmin() {
        User admin = new User();
        admin.setId(1L);
        admin.setAdmin(true);
        UserContext context = new UserContext();
        context.setUser(admin);
        UserContextHolder.create(context);
        Organisation organisation = new Organisation();
        organisation.setId(2L);
        when(organisationRepository.findByUuidAndAccount_AccountAdmin_User_Id(any(), any())).thenReturn(organisation);
    }

    @After
    public void clear() {
        UserContextHolder.clear();
    }

    @Test
    public void theSandboxIsRefusedOnAServerAllowingProductionOnly() {
        EkaConfigRequest request = new EkaConfigRequest();
        request.setEnvironment("sandbox");
        request.setClientId("client-1");
        request.setClientSecret("test-secret-value");
        request.setHipIds(List.of("test-hip"));

        BadRequestError e = assertThrows(BadRequestError.class, () -> service.saveEkaConfig("org-uuid", request));

        assertTrue(e.getMessage(), e.getMessage().contains("not allowed on this server"));
        verifyNoInteractions(configRepository, entityManager);
    }
}
