package org.avni.server.service.abdm;

import com.bugsnag.Bugsnag;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// ABDM's jobs run for each organisation with record sharing on, as that organisation's system user, one at a time; one
// organisation's failure is logged, sent to Bugsnag and kept for the status page, and the next organisation still runs.
public class AbdmJobRunnerTest {
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final String TASK = "visitLinking";

    private final AuthService authService = mock(AuthService.class);
    private final OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);
    private final ExternalSystemConfigRepository configRepository = mock(ExternalSystemConfigRepository.class);
    private final OrganisationRepository organisationRepository = mock(OrganisationRepository.class);
    private final AbdmJobRuns jobRuns = mock(AbdmJobRuns.class);
    private final Bugsnag bugsnag = mock(Bugsnag.class);
    private final List<OrganisationConfig> sharingSwitchedOn = new ArrayList<>();
    private AbdmJobRunner runner;

    @Before
    public void setUp() {
        AbdmAccess access = new AbdmAccess(organisationConfigService, configRepository, new org.avni.server.service.abha.EkaEnvironments("sandbox,production"));
        AbdmOrgContext orgContext = new AbdmOrgContext(authService, organisationRepository, configRepository, mock(PlatformTransactionManager.class));
        runner = new AbdmJobRunner(authService, organisationConfigService, access, orgContext, jobRuns, bugsnag,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(organisationConfigService.findAllWithFeatureEnabled("enableAbdmRecordSharing")).thenReturn(sharingSwitchedOn);
        when(authService.authenticateByUserName(anyString(), any())).thenAnswer(inv -> {
            String username = inv.getArgument(0);
            UserContext context = new UserContext();
            User user = new User();
            user.setUsername(username);
            context.setUser(user);
            if (username.startsWith("system-")) {
                Organisation organisation = new Organisation();
                organisation.setId(Long.parseLong(username.substring("system-".length())));
                context.setOrganisation(organisation);
                user.setOrganisationId(organisation.getId());
            }
            UserContextHolder.create(context);
            return context;
        });
    }

    @After
    public void clear() {
        UserContextHolder.clear();
    }

    @Test
    public void theJobRunsOnlyForOrganisationsWithBothSwitchesAndEkaSettings_asEachOnesSystemUser() throws Exception {
        organisation(1L, true, true, "system-1");
        organisation(2L, false, true, "system-2");
        organisation(3L, true, true, null);
        organisation(4L, true, true, "system-4");
        when(configRepository.getEkaSystemConfig(3L)).thenThrow(new EkaNotConfiguredException("none"));
        List<Long> ranFor = new ArrayList<>();

        runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> ranFor.add(UserContextHolder.getUserContext().getOrganisationId()));

        assertEquals(List.of(1L, 4L), ranFor);
        verify(authService).authenticateByUserName("admin", null);
        verify(authService).authenticateByUserName("system-1", "org-1-uuid");
        verify(authService).authenticateByUserName("system-4", "org-4-uuid");
        verify(authService, never()).authenticateByUserName(eq("system-2"), any());
        verify(jobRuns).succeeded(eq(1L), eq(TASK), any());
        verify(jobRuns).succeeded(eq(4L), eq(TASK), any());
        verifyNoInteractions(bugsnag);
        assertNull(UserContextHolder.getUserContext());
    }

    @Test
    public void oneOrganisationsFailureIsReportedWithTheOrganisation_andTheNextStillRuns() throws Exception {
        organisation(1L, true, true, "system-1");
        organisation(2L, true, true, "system-2");
        IllegalStateException failure = new IllegalStateException("database down");
        List<Long> ranFor = new ArrayList<>();

        runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> {
            Long organisationId = UserContextHolder.getUserContext().getOrganisationId();
            if (organisationId == 1L) throw failure;
            ranFor.add(organisationId);
        });

        assertEquals(List.of(2L), ranFor);
        verify(bugsnag).notify(failure);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(jobRuns).failed(eq(1L), eq(TASK), error.capture(), any());
        assertTrue(error.getValue(), error.getValue().contains("database down"));
        verify(jobRuns, never()).succeeded(eq(1L), any(), any());
        verify(jobRuns).succeeded(eq(2L), eq(TASK), any());
    }

    // F13: without a system user an organisation cannot share records (AbdmAccess), though both its switches are on: the
    // job does not run for it, and its run is recorded as failed, naming the missing setting for the status page. The fix
    // is the organisation's settings, which the page names, so it is not sent to Bugsnag every run.
    @Test
    public void anOrganisationWithBothSwitchesOnButNoSystemUserIsRecordedAsFailed_namingTheSetting() throws Exception {
        organisation(1L, true, true, null);
        List<Long> ranFor = new ArrayList<>();

        runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> ranFor.add(1L));

        assertTrue(ranFor.isEmpty());
        verifyNoInteractions(bugsnag);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(jobRuns).failed(eq(1L), eq(TASK), error.capture(), any());
        assertTrue(error.getValue(), error.getValue().contains("system user"));
        verify(jobRuns, never()).succeeded(anyLong(), any(), any());
    }

    // F13: the same for an organisation with both switches on and no Eka settings; one with a switch off is not sharing by
    // choice, and nothing is recorded.
    @Test
    public void anOrganisationWithBothSwitchesOnButNoEkaSettingsIsRecordedAsFailed_oneSwitchedOffIsNot() throws Exception {
        organisation(1L, true, true, "system-1");
        organisation(2L, false, true, "system-2");
        when(configRepository.getEkaSystemConfig(1L)).thenThrow(new EkaNotConfiguredException("none"));

        runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> fail("nothing runs"));

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(jobRuns).failed(eq(1L), eq(TASK), error.capture(), any());
        assertTrue(error.getValue(), error.getValue().contains("Eka settings"));
        verify(jobRuns, never()).failed(eq(2L), any(), any(), any());
        verifyNoInteractions(bugsnag);
    }

    // F13: each run starts one organisation further on, so the same organisation is not always left the least time.
    @Test
    public void eachRunStartsWithTheNextOrganisation() throws Exception {
        organisation(1L, true, true, "system-1");
        organisation(2L, true, true, "system-2");
        organisation(3L, true, true, "system-3");
        List<List<Long>> runs = new ArrayList<>();

        for (int run = 0; run < 4; run++) {
            List<Long> ranFor = new ArrayList<>();
            runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> ranFor.add(UserContextHolder.getUserContext().getOrganisationId()));
            runs.add(ranFor);
        }

        assertEquals(List.of(List.of(1L, 2L, 3L), List.of(2L, 3L, 1L), List.of(3L, 1L, 2L), List.of(1L, 2L, 3L)), runs);
    }

    // Carried from R2-1: a thread told to stop (the server shutting down) takes no further organisation, and a failure on
    // the way out is neither recorded nor reported: it is the stop, not the organisation.
    @Test
    public void aRunToldToStopTakesNoFurtherOrganisation_andReportsNothing() throws Exception {
        organisation(1L, true, true, "system-1");
        organisation(2L, true, true, "system-2");
        List<Long> ranFor = new ArrayList<>();
        try {
            runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> {
                ranFor.add(UserContextHolder.getUserContext().getOrganisationId());
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Eka POST link not sent: interrupted");
            });
        } finally {
            assertTrue(Thread.interrupted());
        }

        assertEquals(List.of(1L), ranFor);
        verifyNoInteractions(bugsnag);
        verify(jobRuns, never()).failed(anyLong(), any(), any(), any());
        assertNull(UserContextHolder.getUserContext());
    }

    // A system user named that cannot act for the organisation (removed, or another organisation's) is reported.
    @Test
    public void anOrganisationWhoseSystemUserCannotActForItIsReported_notSkippedSilently() throws Exception {
        organisation(1L, true, true, "user-of-no-organisation");
        List<Long> ranFor = new ArrayList<>();

        runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> ranFor.add(1L));

        assertTrue(ranFor.isEmpty());
        verify(bugsnag).notify(any(AbdmOrgContext.SystemUserException.class));
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(jobRuns).failed(eq(1L), eq(TASK), error.capture(), any());
        assertTrue(error.getValue(), error.getValue().contains("system user"));
    }

    @Test
    public void eachOrganisationGetsItsShareOfWhatIsLeftOfTheRunsTime() throws Exception {
        organisation(1L, true, true, "system-1");
        organisation(2L, true, true, "system-2");
        List<Instant> deadlines = new ArrayList<>();

        runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadlines::add);

        assertEquals(List.of(NOW.plus(Duration.ofMinutes(2)), NOW.plus(Duration.ofMinutes(4))), deadlines);
    }

    @Test
    public void whenTheOrganisationsCannotBeReadTheFailureIsReported() {
        RuntimeException failure = new RuntimeException("no database");
        when(organisationConfigService.findAllWithFeatureEnabled(anyString())).thenThrow(failure);

        runner.forEachOrganisation(TASK, Duration.ofMinutes(4), deadline -> fail("nothing runs"));

        verify(bugsnag).notify(failure);
    }

    // Erasing records when their consent ends does not lapse with the switch, so this work names its organisations.
    @Test
    public void namedOrganisationsAreRunWhateverTheirSwitches_asEachOnesSystemUser_andNoOthers() throws Exception {
        organisation(1L, false, false, "system-1");
        organisation(2L, true, true, "system-2");
        List<Long> ranFor = new ArrayList<>();

        runner.forOrganisations(TASK, Duration.ofMinutes(4), () -> List.of(1L), deadline -> ranFor.add(UserContextHolder.getUserContext().getOrganisationId()));

        assertEquals(List.of(1L), ranFor);
        verify(authService).authenticateByUserName("system-1", "org-1-uuid");
        verify(authService, never()).authenticateByUserName(eq("system-2"), any());
        verify(organisationConfigService, never()).findAllWithFeatureEnabled(anyString());
        verify(jobRuns).succeeded(eq(1L), eq(TASK), any());
        assertNull(UserContextHolder.getUserContext());
    }

    @Test
    public void aNamedOrganisationWithNoSystemUserIsReported_notSkippedSilently() throws Exception {
        organisation(1L, false, false, null);
        List<Long> ranFor = new ArrayList<>();

        runner.forOrganisations(TASK, Duration.ofMinutes(4), () -> List.of(1L), deadline -> ranFor.add(1L));

        assertTrue(ranFor.isEmpty());
        verify(bugsnag).notify(any(AbdmOrgContext.SystemUserException.class));
        verify(jobRuns).failed(eq(1L), eq(TASK), anyString(), any());
    }

    // F1: a duty such as erasure waits on no setting. When the system user is missing or cannot act for the organisation,
    // the work given for that case runs for it with no user logged in, and the broken system user is still reported.
    @Test
    public void whenTheSystemUserCannotActTheWorkWithoutALoginRunsForTheOrganisation_andItIsStillReported() throws Exception {
        organisation(1L, false, false, "user-of-no-organisation");
        organisation(2L, false, false, null);
        organisation(3L, false, false, "system-3");
        List<Long> loggedIn = new ArrayList<>();
        List<Long> withoutLogin = new ArrayList<>();
        List<Object> usersDuringWithoutLogin = new ArrayList<>();

        runner.forOrganisations(TASK, Duration.ofMinutes(4), () -> List.of(1L, 2L, 3L),
                deadline -> loggedIn.add(UserContextHolder.getUserContext().getOrganisationId()),
                organisationId -> {
                    withoutLogin.add(organisationId);
                    usersDuringWithoutLogin.add(UserContextHolder.getUserContext());
                });

        assertEquals(List.of(3L), loggedIn);
        assertEquals(List.of(1L, 2L), withoutLogin);
        assertEquals(java.util.Arrays.asList(null, null), usersDuringWithoutLogin);
        verify(bugsnag, times(2)).notify(any(AbdmOrgContext.SystemUserException.class));
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(jobRuns).failed(eq(1L), eq(TASK), error.capture(), any());
        assertTrue(error.getValue(), error.getValue().contains("system user") && error.getValue().contains("without"));
        verify(jobRuns).failed(eq(2L), eq(TASK), anyString(), any());
        verify(jobRuns, never()).succeeded(eq(1L), any(), any());
        verify(jobRuns).succeeded(eq(3L), eq(TASK), any());
        assertNull(UserContextHolder.getUserContext());
    }

    @Test
    public void aFailureOfTheWorkWithoutALoginIsReportedToo() throws Exception {
        organisation(1L, false, false, null);
        IllegalStateException failure = new IllegalStateException("database down");

        runner.forOrganisations(TASK, Duration.ofMinutes(4), () -> List.of(1L), deadline -> fail("no login"), organisationId -> {
            throw failure;
        });

        verify(bugsnag).notify(failure);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(jobRuns).failed(eq(1L), eq(TASK), error.capture(), any());
        assertTrue(error.getValue(), error.getValue().contains("database down"));
        // Fix round 1: the system user that could not act is reported with it, not lost behind it.
        assertTrue(error.getValue(), error.getValue().contains("system user"));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0] instanceof AbdmOrgContext.SystemUserException);
        // Fix round 2: Bugsnag drops suppressed exceptions, so the system user's is sent on its own too.
        verify(bugsnag).notify(any(AbdmOrgContext.SystemUserException.class));
    }

    private void organisation(long id, boolean abha, boolean sharing, String systemUser) throws Exception {
        OrganisationConfig config = new OrganisationConfig();
        config.setOrganisationId(id);
        config.setSettings(new JsonObject().with("enableAbha", abha).with("enableAbdmRecordSharing", sharing));
        if (sharing) sharingSwitchedOn.add(config);
        when(organisationConfigService.getOrganisationConfigByOrgId(id)).thenReturn(config);
        JsonObject eka = new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.HIP_IDS, List.of("hip-" + id))
                .with(EkaSystemConfig.CLIENT_ID, "m1-client")
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-client")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted")
                .with(EkaSystemConfig.ENCRYPTED_WEBHOOK_SIGNING_KEY, "encrypted-key");
        if (systemUser != null) eka.with(EkaSystemConfig.AVNI_SYSTEM_USER, systemUser);
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setOrganisationId(id);
        row.setConfig(eka);
        when(configRepository.getEkaSystemConfig(id)).thenReturn(new EkaSystemConfig(row));
        Organisation organisation = new Organisation();
        organisation.setId(id);
        organisation.setName("Org " + id);
        organisation.setUuid("org-" + id + "-uuid");
        when(organisationRepository.findOne(id)).thenReturn(organisation);
    }
}
