package org.avni.server.service.abdm.eka;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.EncounterService;
import org.avni.server.service.abdm.AbdmHousekeepingJob;
import org.avni.server.service.abdm.AbdmInbox;
import org.avni.server.service.abdm.AbdmJobRunner;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmScheduler;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hiu.AbdmHiuDataService;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.avni.server.service.abha.EkaToken;
import org.avni.server.service.abha.EkaTokenService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestEncounterTypeService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Two organisations share records, each under its own HIP ID and record-sharing client, with a visit queued each. The
// link job, run as scheduled, acts for one organisation at a time as its system user: one organisation's turn takes
// none of the other's queued visits and calls Eka with its own HIP ID and its own sharing client's token only.
// Not @Transactional: the job and the save hook run in transactions of their own.
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbdmLinkingPerOrganisationIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private ExternalSystemConfigRepository externalSystemConfigRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private AbdmJobRunner realRunner;
    @Autowired
    private AbdmOrgContext orgContext;
    @Autowired
    private AbdmSettings settings;
    @Autowired
    private AbdmHipStore hipStore;
    @Autowired
    private EkaHipService ekaHipService;

    private final EkaTokenService tokens = mock(EkaTokenService.class);
    private WireMockServer eka;
    private EkaAbdmClient realClient;

    private record Org(long id, String hipId, String patientUuid, String visitUuid) {
    }

    private Org orgA;
    private Org orgB;

    @Before
    public void twoSharingOrganisationsWithAVisitQueuedEach() throws Exception {
        orgA = sharingOrganisation("abdm-org-a", "hip-a", "12345678568912");
        orgB = sharingOrganisation("abdm-org-b", "hip-b", "12345678568913");
        when(tokens.getSharingToken(orgA.id())).thenReturn(new EkaToken("token-a", Instant.now().plusSeconds(3600)));
        when(tokens.getSharingToken(orgB.id())).thenReturn(new EkaToken("token-b", Instant.now().plusSeconds(3600)));

        eka = new WireMockServer(options().dynamicPort());
        eka.start();
        eka.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(EkaHipService.LINK_PATH)).willReturn(aResponse().withStatus(202)));
        String baseUrl = "http://localhost:" + eka.port();
        EkaAbdmClient client = new EkaAbdmClient(orgContext, externalSystemConfigRepository, tokens, mock(AbdmMessageLog.class),
                config -> baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofMillis(10));
        Object target = AopTestUtils.getTargetObject(ekaHipService);
        realClient = (EkaAbdmClient) ReflectionTestUtils.getField(target, "eka");
        ReflectionTestUtils.setField(target, "eka", client);

        assertEquals(List.of(orgA.visitUuid() + " hip-a QUEUED"), careContexts(orgA));
        assertEquals(List.of(orgB.visitUuid() + " hip-b QUEUED"), careContexts(orgB));
    }

    @After
    public void restoreEka() {
        Object target = AopTestUtils.getTargetObject(ekaHipService);
        ReflectionTestUtils.setField(target, "eka", realClient);
        if (eka != null) eka.stop();
    }

    @Test
    public void eachOrganisationsTurnLinksOnlyItsOwnVisits_withItsOwnHipIdAndSharingToken() throws Exception {
        Map<String, Object> rowOfBBefore = careContextRow(orgB);

        linkingJobTurnOf(orgA).linkQueuedVisits();

        List<LoggedRequest> calls = eka.findAll(postRequestedFor(urlEqualTo(EkaHipService.LINK_PATH)));
        assertEquals(1, calls.size());
        assertEquals("hip-a", calls.get(0).getHeader(EkaAbdmClient.H_HIP_ID));
        assertEquals("Bearer token-a", calls.get(0).getHeader("Authorization"));
        assertEquals(orgA.patientUuid(), calls.get(0).getHeader(EkaAbdmClient.H_PARTNER_PATIENT_ID));
        verify(tokens, never()).getSharingToken(orgB.id());
        verify(tokens, never()).getAccessToken(any());
        assertEquals(List.of(orgA.visitUuid() + " hip-a LINK_REQUESTED"), careContexts(orgA));
        assertEquals(rowOfBBefore, careContextRow(orgB));

        Map<String, Object> rowOfABefore = careContextRow(orgA);
        eka.resetRequests();

        linkingJobTurnOf(orgB).linkQueuedVisits();

        calls = eka.findAll(postRequestedFor(urlEqualTo(EkaHipService.LINK_PATH)));
        assertEquals(1, calls.size());
        assertEquals("hip-b", calls.get(0).getHeader(EkaAbdmClient.H_HIP_ID));
        assertEquals("Bearer token-b", calls.get(0).getHeader("Authorization"));
        assertEquals(orgB.patientUuid(), calls.get(0).getHeader(EkaAbdmClient.H_PARTNER_PATIENT_ID));
        assertEquals(List.of(orgB.visitUuid() + " hip-b LINK_REQUESTED"), careContexts(orgB));
        assertEquals(rowOfABefore, careContextRow(orgA));
    }

    // The job as scheduled, through the real runner, which runs the turn of the one organisation named.
    @SuppressWarnings("unchecked")
    private AbdmHousekeepingJob linkingJobTurnOf(Org org) {
        AbdmJobRunner runner = mock(AbdmJobRunner.class);
        doAnswer(inv -> {
            realRunner.forOrganisations(inv.getArgument(0), inv.getArgument(1), () -> List.of(org.id()), (Consumer<Instant>) inv.getArgument(2));
            return null;
        }).when(runner).forEachOrganisation(eq(AbdmHousekeepingJob.VISIT_LINKING), any(), any());
        return new AbdmHousekeepingJob(runner, mock(AbdmScheduler.class), settings, mock(AbdmMessageLog.class), mock(AbdmInbox.class),
                mock(AbdmHiuDataService.class), mock(AbdmHiuStore.class), hipStore, ekaHipService, mock(EkaHiuService.class), 30);
    }

    private Org sharingOrganisation(String suffix, String hipId, String abhaNumber) {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation(suffix);
        TestDataSetupService.TestCatchmentData catchment = testDataSetupService.setupACatchment();
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build());
        EncounterType encounterType = testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("OPD Visit", subjectType).getEncounterType();
        Individual patient = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).build());
        long organisationId = organisation.getOrganisationId();

        ExternalSystemConfig row = new ExternalSystemConfig();
        row.assignUUID();
        row.setSystemName(SystemName.Eka);
        row.setConfig(new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.CLIENT_ID, "m1-client-" + suffix)
                .with(EkaSystemConfig.ENCRYPTED_CLIENT_SECRET, "encrypted-secret")
                .with(EkaSystemConfig.HIP_IDS, List.of(hipId))
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-client-" + suffix)
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-sharing-secret")
                .with(EkaSystemConfig.AVNI_SYSTEM_USER, organisation.getUser().getUsername())
                .with(EkaSystemConfig.ENCRYPTED_WEBHOOK_SIGNING_KEY, "encrypted-signing-key"));
        externalSystemConfigRepository.save(row);
        long userId = organisation.getUser().getId();
        asSuperAdmin(jdbc -> {
            jdbc.update("update public.organisation_config set settings = coalesce(settings, '{}'::jsonb) || " +
                    "'{\"enableAbha\": true, \"enableAbdmRecordSharing\": true}'::jsonb where organisation_id = ?", organisationId);
            jdbc.update("insert into public.abha_link (uuid, organisation_id, individual_id, abha_number, created_by_id, last_modified_by_id, " +
                    "created_date_time, last_modified_date_time) values (?, ?, ?, ?, ?, ?, now(), now())",
                    UUID.randomUUID().toString(), organisationId, patient.getId(), abhaNumber, userId, userId);
            jdbc.update("insert into public.abha_event (uuid, organisation_id, subject_uuid, user_id, hip_id, action, route, abha_number, " +
                    "eka_oid, occurred_at, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                    "values (?, ?, ?, ?, ?, 'verified', 'abha_number', ?, ?, now(), ?, ?, now(), now())",
                    UUID.randomUUID().toString(), organisationId, patient.getUuid(), userId, hipId, abhaNumber, "oid-" + suffix, userId, userId);
        }, organisation.getUser().getUsername());

        Encounter visit = new Encounter();
        visit.assignUUID();
        visit.setEncounterType(encounterType);
        visit.setIndividual(patient);
        visit.setEncounterDateTime(DateTime.now().minusHours(1), organisation.getUser());
        visit.setObservations(new ObservationCollection());
        visit.setCancelObservations(new ObservationCollection());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> encounterService.save(visit));
        return new Org(organisationId, hipId, patient.getUuid(), visit.getUuid());
    }

    // "<reference> <hip id> <status>" of the organisation's visits.
    private List<String> careContexts(Org org) {
        List<String>[] rows = new List[1];
        asSuperAdmin(jdbc -> rows[0] = jdbc.query("select reference, hip_id, status from public.abdm_care_context where organisation_id = ? order by id",
                (rs, i) -> rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3), org.id()), null);
        return rows[0];
    }

    private Map<String, Object> careContextRow(Org org) {
        Map<String, Object>[] row = new Map[1];
        asSuperAdmin(jdbc -> row[0] = jdbc.queryForMap("select * from public.abdm_care_context where organisation_id = ?", org.id()), null);
        return row[0];
    }

    private void asSuperAdmin(Consumer<JdbcTemplate> work, String thenActAs) {
        setUser(userRepository.getDefaultSuperAdmin());
        try {
            work.accept(new JdbcTemplate(dataSource));
        } finally {
            if (thenActAs != null) setUser(thenActAs);
        }
    }
}
