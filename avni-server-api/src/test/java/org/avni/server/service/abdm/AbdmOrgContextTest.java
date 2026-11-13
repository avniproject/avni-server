package org.avni.server.service.abdm;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.junit.After;
import org.junit.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// ABDM work with no Avni user runs as its organisation's system user, named in that organisation's Eka settings.
public class AbdmOrgContextTest {
    private final AuthService authService = mock(AuthService.class);
    private final OrganisationRepository organisationRepository = mock(OrganisationRepository.class);
    private final ExternalSystemConfigRepository configRepository = mock(ExternalSystemConfigRepository.class);
    private final AbdmOrgContext orgContext = new AbdmOrgContext(authService, organisationRepository, configRepository,
            mock(PlatformTransactionManager.class));

    @After
    public void clear() {
        UserContextHolder.clear();
    }

    // The login names the organisation, so the database's row filter is that organisation's whoever the user is.
    @Test
    public void theWorkRunsAsTheOrganisationsSystemUser_inThatOrganisation_andTheThreadsUserIsPutBack() {
        UserContext before = contextOf(9L, "someone");
        UserContextHolder.create(before);
        onRecord(7L, "org-seven-uuid");
        logsInAs("system-7", 7L);
        AtomicReference<Long> actingFor = new AtomicReference<>();

        orgContext.runAs(new AbdmOrganisation(7L, "Org seven", "system-7"), () -> actingFor.set(orgContext.organisationId()));

        assertEquals(Long.valueOf(7L), actingFor.get());
        verify(authService).authenticateByUserName(eq("system-7"), eq("org-seven-uuid"));
        assertSame(before, UserContextHolder.getUserContext());
    }

    // An account administrator acting with no organisation named reads every organisation's rows; even with one named, it
    // is no organisation's own user.
    @Test
    public void anAccountAdministratorAsTheSystemUserIsAnError_andNothingRuns() {
        onRecord(7L, "org-seven-uuid");
        when(authService.authenticateByUserName(eq("system-7"), any())).thenAnswer(inv -> {
            UserContext context = contextOf(7L, "system-7");
            context.getUser().setAdmin(true);
            UserContextHolder.create(context);
            return context;
        });
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        AbdmOrgContext.SystemUserException e = assertThrows(AbdmOrgContext.SystemUserException.class,
                () -> orgContext.runAs(new AbdmOrganisation(7L, "Org seven", "system-7"), () -> ran.set(true)));

        assertFalse(ran.get());
        assertTrue(e.getMessage(), e.getMessage().contains("account administrator"));
        assertNull(UserContextHolder.getUserContext());
    }

    // Logging in with an organisation named makes the session that organisation's, so the user's own organisation decides.
    @Test
    public void aUserOfAnotherOrganisationLoggedInForThisOneIsAnError_andNothingRuns() {
        onRecord(7L, "org-seven-uuid");
        when(authService.authenticateByUserName(eq("system-8"), any())).thenAnswer(inv -> {
            UserContext context = contextOf(7L, "system-8");
            context.getUser().setOrganisationId(8L);
            UserContextHolder.create(context);
            return context;
        });
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        assertThrows(AbdmOrgContext.SystemUserException.class,
                () -> orgContext.runAs(new AbdmOrganisation(7L, "Org seven", "system-8"), () -> ran.set(true)));

        assertFalse(ran.get());
    }

    @Test
    public void anOrganisationNotOnRecordIsAnError_andNoOneLogsIn() {
        assertThrows(AbdmOrgContext.SystemUserException.class,
                () -> orgContext.runAs(new AbdmOrganisation(7L, "Org seven", "system-7"), () -> {
                }));

        verifyNoInteractions(authService);
    }

    @Test
    public void anOrganisationWithNoSystemUserIsAnError_andNothingRuns() {
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        AbdmOrgContext.SystemUserException e = assertThrows(AbdmOrgContext.SystemUserException.class,
                () -> orgContext.runAs(new AbdmOrganisation(7L, "Org seven", null), () -> ran.set(true)));

        assertFalse(ran.get());
        assertTrue(e.getMessage(), e.getMessage().contains("organisation 7"));
        verifyNoInteractions(authService);
    }

    @Test
    public void aSystemUserOfAnotherOrganisationIsAnError_andNothingRuns() {
        onRecord(7L, "org-seven-uuid");
        logsInAs("system-7", 8L);
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        assertThrows(AbdmOrgContext.SystemUserException.class,
                () -> orgContext.runAs(new AbdmOrganisation(7L, "Org seven", "system-7"), () -> ran.set(true)));

        assertFalse(ran.get());
        assertNull(UserContextHolder.getUserContext());
    }

    @Test
    public void aSystemUserThatCannotLogInIsAnError() {
        onRecord(7L, "org-seven-uuid");
        when(authService.authenticateByUserName(eq("gone"), any())).thenThrow(new RuntimeException("No user, or not logged in"));

        assertThrows(AbdmOrgContext.SystemUserException.class,
                () -> orgContext.runAs(new AbdmOrganisation(7L, "Org seven", "gone"), () -> {
                }));
    }

    @Test
    public void theOrganisationIsTheCurrentUsersOnly_withNoFallback() {
        assertNull(orgContext.organisationId());

        UserContextHolder.create(contextOf(9L, "someone"));

        assertEquals(Long.valueOf(9L), orgContext.organisationId());
        verifyNoInteractions(organisationRepository);
    }

    @Test
    public void anOrganisationIsReadWithTheSystemUserFromItsEkaSettings() throws Exception {
        Organisation organisation = new Organisation();
        organisation.setId(7L);
        organisation.setName("Org seven");
        when(organisationRepository.findOne(7L)).thenReturn(organisation);
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setConfig(new JsonObject().with(EkaSystemConfig.AVNI_SYSTEM_USER, "system-7"));
        when(configRepository.getEkaSystemConfig(7L)).thenReturn(new EkaSystemConfig(row));

        assertEquals(new AbdmOrganisation(7L, "Org seven", "system-7"), orgContext.organisation(7L));

        when(configRepository.getEkaSystemConfig(7L)).thenThrow(new EkaNotConfiguredException("none"));
        assertEquals(new AbdmOrganisation(7L, "Org seven", null), orgContext.organisation(7L));
    }

    private void onRecord(long organisationId, String uuid) {
        Organisation organisation = new Organisation();
        organisation.setId(organisationId);
        organisation.setUuid(uuid);
        when(organisationRepository.findOne(organisationId)).thenReturn(organisation);
    }

    private void logsInAs(String username, long organisationId) {
        when(authService.authenticateByUserName(eq(username), any())).thenAnswer(inv -> {
            UserContext context = contextOf(organisationId, username);
            UserContextHolder.create(context);
            return context;
        });
    }

    private static UserContext contextOf(long organisationId, String username) {
        UserContext context = new UserContext();
        Organisation organisation = new Organisation();
        organisation.setId(organisationId);
        context.setOrganisation(organisation);
        User user = new User();
        user.setUsername(username);
        user.setOrganisationId(organisationId);
        context.setUser(user);
        return context;
    }
}
