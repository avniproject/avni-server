package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.EncounterService;
import org.avni.server.service.IndividualService;
import org.avni.server.service.ProgramEncounterService;
import org.avni.server.service.abdm.hip.AbdmCareContextAspect;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmVisitQueue;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.eka.EkaResponse;
import org.avni.server.service.abha.AbhaLinkAspect;
import org.avni.server.service.abha.AbhaLinkService;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.ProgramEncounterRequest;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJPrecedenceInformation;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Visit and subject saves reach ABDM and ABHA only through the save hooks, which check the organisation's switch first.
// Not @Transactional: the care-context queue is written after the save commits, so each save here commits.
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbdmSaveHooksIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String HIP_ID = "hip-1";
    private static final String ABHA_NUMBER = "12-3456-7856-8912";

    // Spies are swapped into the hooks per test: a second context with spy beans fails here, loading ehcache.xml twice.
    @Autowired
    private AbdmCareContextAspect abdmCareContextAspect;
    @Autowired
    private AbhaLinkAspect abhaLinkAspect;
    @Autowired
    private AbdmAccess realAccess;
    @Autowired
    private AbdmVisitQueue realVisitQueue;
    @Autowired
    private AbdmHipStore realHipStore;
    @Autowired
    private AbdmSettings realSettings;
    @Autowired
    private AbhaLinkService realAbhaLinkService;
    private AbdmVisitQueue abdmVisitQueue;
    private AbdmHipStore abdmHipStore;
    private AbdmSettings abdmSettings;
    private AbhaLinkService abhaLinkService;

    @Autowired
    private EncounterService encounterService;
    @Autowired
    private ProgramEncounterService programEncounterService;
    @Autowired
    private IndividualService individualService;
    @Autowired
    private EncounterRepository encounterRepository;
    @Autowired
    private ExternalSystemConfigRepository externalSystemConfigRepository;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    private DataSource dataSource;

    private TestDataSetupService.TestOrganisationData organisation;
    private TestDataSetupService.TestCatchmentData catchment;
    private SubjectType subjectType;
    private EncounterType encounterType;
    private EncounterType programEncounterType;
    private Concept abhaNumber;
    private Individual patient;
    private ProgramEnrolment enrolment;

    @Autowired
    private org.avni.server.service.abdm.hip.AbdmVerifiedAbhaAspect verifiedAbhaAspect;
    @Autowired
    private EkaHipService ekaHipService;
    @Autowired
    private org.avni.server.dao.IndividualRepository individualRepository;
    private EkaAbdmClient realEka;
    private final EkaAbdmClient eka = mock(EkaAbdmClient.class);

    @Before
    public void answerLinksAsEka() {
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(ekaHipService);
        realEka = (EkaAbdmClient) ReflectionTestUtils.getField(target, "eka");
        ReflectionTestUtils.setField(target, "eka", eka);
        when(eka.post(any(), any(), any())).thenReturn(new EkaResponse("r-1", 202, null));
    }

    @After
    public void restoreEka() {
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(ekaHipService);
        ReflectionTestUtils.setField(target, "eka", realEka);
    }

    @Before
    public void spyOnTheHooks() {
        abdmHipStore = spy(realHipStore);
        abdmSettings = spy(realSettings);
        ReflectionTestUtils.setField(realVisitQueue, "store", abdmHipStore);
        ReflectionTestUtils.setField(realVisitQueue, "settings", abdmSettings);
        abdmVisitQueue = spy(realVisitQueue);
        ReflectionTestUtils.setField(abdmCareContextAspect, "abdmVisitQueue", abdmVisitQueue);
        ReflectionTestUtils.setField(verifiedAbhaAspect, "abdmVisitQueue", abdmVisitQueue);
        ReflectionTestUtils.setField(verifiedAbhaAspect, "store", abdmHipStore);
        abhaLinkService = spy(realAbhaLinkService);
        ReflectionTestUtils.setField(abhaLinkAspect, "abhaLinkService", abhaLinkService);
    }

    @After
    public void restoreTheHooks() {
        ReflectionTestUtils.setField(abdmCareContextAspect, "abdmVisitQueue", realVisitQueue);
        ReflectionTestUtils.setField(abdmCareContextAspect, "abdmAccess", realAccess);
        ReflectionTestUtils.setField(realVisitQueue, "store", realHipStore);
        ReflectionTestUtils.setField(realVisitQueue, "settings", realSettings);
        ReflectionTestUtils.setField(abhaLinkAspect, "abhaLinkService", realAbhaLinkService);
        ReflectionTestUtils.setField(verifiedAbhaAspect, "abdmVisitQueue", realVisitQueue);
        ReflectionTestUtils.setField(verifiedAbhaAspect, "store", realHipStore);
    }

    @Before
    public void createOrganisationWithAPatient() {
        organisation = testDataSetupService.setupOrganisation("abdmhooks");
        catchment = testDataSetupService.setupACatchment();
        subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build());
        Program program = testProgramService.addProgram(new ProgramBuilder().withName("Hypertension").build(), subjectType);
        encounterType = testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("OPD Visit", subjectType).getEncounterType();
        programEncounterType = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping("Hypertension Follow-up", subjectType, program).getEncounterType();
        abhaNumber = testConceptService.createConcept(AbhaLinkService.ABHA_NUMBER_CONCEPT, ConceptDataType.Text);
        patient = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).build());
        enrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity()
                .setProgram(program).setIndividual(patient).setObservations(new ObservationCollection()).build());
        saveEkaSettings();
        patientHoldsAnAbha();
    }

    @Test
    public void visitsSavedWithRecordSharingOffNeverReachTheQueue() {
        switches(true, false);

        Encounter visit = inTransaction(() -> encounterService.save(completedVisit()));
        ProgramEncounter programVisit = inTransaction(() -> programEncounterService.save(completedProgramVisit()));

        verify(abdmVisitQueue, never()).queue(any());
        verify(abdmSettings, never()).find();
        assertEquals(Collections.emptyList(), careContexts());
        assertTrue(saved(visit) && saved(programVisit));
    }

    @Test
    public void aVisitOfAPatientHoldingAnAbhaIsQueuedOnceTheSaveCommits() {
        switches(true, true);

        Encounter visit = inTransaction(() -> {
            Encounter saved = encounterService.save(completedVisit());
            verify(abdmVisitQueue).queue(saved);
            verify(abdmHipStore, never()).queueCareContexts(anyLong(), any(), any(), any(), any());
            return saved;
        });
        ProgramEncounter programVisit = inTransaction(() -> programEncounterService.save(completedProgramVisit()));

        assertEquals(List.of(visit.getUuid() + " " + HIP_ID + " QUEUED", programVisit.getUuid() + " " + HIP_ID + " QUEUED"), careContexts());
    }

    // The sync push of a programme visit (saveProgramEncounter) and the external API's PATCH of a visit (patchEncounter)
    // save through a call inside the service, which no hook sees; their own entry points carry the hook.
    @Test
    public void visitsSavedThroughEveryServiceEntryPointAreQueued() throws Exception {
        switches(true, true);
        Encounter stored = completedVisit();
        inTransaction(() -> encounterRepository.save(stored));

        ProgramEncounter pushed = inTransaction(() -> {
            try {
                return programEncounterService.saveProgramEncounter(programVisitRequest());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Encounter patched = inTransaction(() -> {
            try {
                return encounterService.patchEncounter(encounterRepository.findByUuid(stored.getUuid()), new java.util.HashMap<>());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(List.of(pushed.getUuid() + " " + HIP_ID + " QUEUED", patched.getUuid() + " " + HIP_ID + " QUEUED"), careContexts());
    }

    // H12: the queue write fails in the database itself, so a write inside the visit's transaction would abort the save.
    @Test
    public void aQueueWriteThatFailsInTheDatabaseLeavesTheVisitSaved() {
        switches(true, true);
        doAnswer(inv -> {
            namedJdbc.getJdbcOperations().execute("select 1/0");
            return null;
        }).when(abdmHipStore).queueCareContexts(anyLong(), any(), any(), any(), any());

        Encounter visit = inTransaction(() -> encounterService.save(completedVisit()));
        ProgramEncounter programVisit = inTransaction(() -> programEncounterService.save(completedProgramVisit()));

        verify(abdmHipStore, times(2)).queueCareContexts(anyLong(), any(), any(), any(), any());
        assertTrue(saved(visit));
        assertTrue(saved(programVisit));
        assertEquals(Collections.emptyList(), careContexts());
    }

    // A failed statement aborts the save's transaction, after which the commit rolls the visit back without an error. So
    // a database error in the hook's reads fails the save loudly, and the sync retries, instead of losing the visit.
    @Test
    public void aDatabaseErrorWhileReadingTheSwitchFailsTheSaveInsteadOfLosingIt() {
        switches(true, true);
        AbdmAccess failing = spy(realAccess);
        doAnswer(inv -> {
            namedJdbc.getJdbcOperations().execute("select 1/0");
            return true;
        }).when(failing).recordSharingOn();
        ReflectionTestUtils.setField(abdmCareContextAspect, "abdmAccess", failing);
        Encounter visit = completedVisit();

        assertThrows(DataAccessException.class, () -> inTransaction(() -> encounterService.save(visit)));
        assertFalse(saved(visit));
    }

    @Test
    public void aDatabaseErrorWhileReadingTheEkaSettingsFailsTheSaveInsteadOfLosingIt() {
        switches(true, true);
        doAnswer(inv -> {
            namedJdbc.getJdbcOperations().execute("select 1/0");
            return null;
        }).when(abdmSettings).find();
        ProgramEncounter visit = completedProgramVisit();

        assertThrows(DataAccessException.class, () -> inTransaction(() -> programEncounterService.save(visit)));
        assertFalse(saved(visit));
    }

    @Test
    public void aSubjectSavedWithAbhaOffNeverReachesTheAbhaLink() {
        switches(false, false);

        Individual subject = inTransaction(() -> individualService.save(subjectWithAnAbha()));
        inTransaction(() -> individualService.voidSubject(subject));

        verify(abhaLinkService, never()).syncLink(any());
        assertEquals(Integer.valueOf(0), abhaLinks(subject));
    }

    // The link is written inside the save's transaction: it rolls back with the save and commits with it.
    @Test
    public void aSubjectSavedWithAbhaOnIsLinkedInsideTheSaveTransaction() {
        switches(true, false);
        Individual rolledBack = subjectWithAnAbha();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            individualService.save(rolledBack);
            status.setRollbackOnly();
        });
        assertEquals(Integer.valueOf(0), abhaLinks(rolledBack));

        Individual subject = inTransaction(() -> individualService.save(subjectWithAnAbha()));

        verify(abhaLinkService, times(2)).syncLink(any());
        assertEquals(Integer.valueOf(1), abhaLinks(subject));
    }

    @Test
    public void aFailureToKeepTheAbhaLinkFailsTheSave() {
        switches(true, false);
        doThrow(new IllegalStateException("link failed")).when(abhaLinkService).syncLink(any());
        Individual subject = subjectWithAnAbha();

        assertThrows(IllegalStateException.class, () -> inTransaction(() -> individualService.save(subject)));

        setUser(userRepository.getDefaultSuperAdmin());
        assertEquals(Integer.valueOf(0), new JdbcTemplate(dataSource).queryForObject("select count(*) from public.individual where uuid = ?", Integer.class, subject.getUuid()));
    }

    // ---- a corrected ABHA, verified before the corrected form syncs

    private static final String CORRECTED_ABHA = "12345678568913";

    // The worker typed a wrong ABHA, then verified the right one. The phone posts the verified record first: its hook
    // queues the visit, but the record still holds the wrong ABHA, so the job finds nothing verified and the visit waits.
    // When the corrected form syncs, the visit is queued again, and the next job run links it.
    @Test
    public void aCorrectedAbhaVerifiedBeforeTheFormSyncsIsLinkedOnceTheFormSyncs() {
        switches(true, true);
        Encounter visit = inTransaction(() -> encounterService.save(completedVisit()));
        verifiedAbhaRecord(CORRECTED_ABHA);
        verifiedAbhaAspect.requeueVisitsWaitingForIt(verifiedAbhaRequest(CORRECTED_ABHA), true);

        linkingJob().linkQueuedVisits();

        assertEquals(List.of(visit.getUuid() + " " + HIP_ID + " AWAITING_VERIFIED_ABHA"), careContexts());
        verify(eka, never()).post(eq(EkaHipService.LINK_PATH), any(), any());

        inTransaction(() -> individualService.save(withAbhaNumber(CORRECTED_ABHA)));

        assertEquals(List.of(visit.getUuid() + " " + HIP_ID + " QUEUED"), careContexts());

        linkingJob().linkQueuedVisits();

        assertEquals(List.of(visit.getUuid() + " " + HIP_ID + " LINK_REQUESTED"), careContexts());
        verify(eka).post(eq(EkaHipService.LINK_PATH), argThat(body -> "oid-verified-1".equals(body.path("oid").asText())), any());
    }

    // Most saves of a patient leave their ABHA as it was and have no visit waiting for a verified one: the hook writes
    // nothing and opens no transaction of its own.
    @Test
    public void aSubjectSaveThatLeavesItsAbhaAsItWasWithNothingWaitingDoesNothing() {
        switches(true, true);

        inTransaction(() -> individualService.save(withAbhaNumber("12345678568912")));

        verify(abdmVisitQueue, never()).requeueIfAbhaVerified(any());
        verify(abdmHipStore, never()).requeueForVerifiedAbha(any());
    }

    @Test
    public void withRecordSharingOffACorrectedAbhaSyncingQueuesNothing() {
        switches(true, true);
        Encounter visit = inTransaction(() -> encounterService.save(completedVisit()));
        linkingJob().linkQueuedVisits();
        verifiedAbhaRecord(CORRECTED_ABHA);
        switches(true, false);

        inTransaction(() -> individualService.save(withAbhaNumber(CORRECTED_ABHA)));

        assertEquals(List.of(visit.getUuid() + " " + HIP_ID + " AWAITING_VERIFIED_ABHA"), careContexts());
    }

    // The job as scheduled, run here as the organisation's user, with Eka answering every link with 202.
    @SuppressWarnings("unchecked")
    private AbdmHousekeepingJob linkingJob() {
        AbdmJobRunner runner = mock(AbdmJobRunner.class);
        doAnswer(inv -> {
            ((java.util.function.Consumer<java.time.Instant>) inv.getArgument(2)).accept(java.time.Instant.now().plusSeconds(240));
            return null;
        }).when(runner).forEachOrganisation(any(), any(), any());
        return new AbdmHousekeepingJob(runner, mock(AbdmScheduler.class), realSettings, mock(AbdmMessageLog.class), mock(AbdmInbox.class),
                mock(org.avni.server.service.abdm.hiu.AbdmHiuDataService.class), mock(org.avni.server.service.abdm.hiu.AbdmHiuStore.class),
                realHipStore, ekaHipService, mock(org.avni.server.service.abdm.eka.EkaHiuService.class), java.time.Clock.systemUTC(), 30);
    }

    private Individual withAbhaNumber(String number) {
        Individual saved = individualRepository.findByUuid(patient.getUuid());
        ObservationCollection observations = new ObservationCollection();
        observations.put(abhaNumber.getUuid(), number);
        saved.setObservations(observations);
        return saved;
    }

    private void verifiedAbhaRecord(String abhaNumber) {
        asSuperAdmin(jdbc -> jdbc.update("insert into public.abha_event (uuid, organisation_id, subject_uuid, user_id, hip_id, action, route, " +
                        "abha_number, abha_address, eka_oid, occurred_at, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                        "values (?, ?, ?, ?, ?, 'verified', 'abha_number', ?, 'asha.real@sbx', 'oid-verified-1', now(), ?, ?, now(), now())",
                UUID.randomUUID().toString(), organisation.getOrganisationId(), patient.getUuid(), organisation.getUser().getId(), HIP_ID,
                abhaNumber, organisation.getUser().getId(), organisation.getUser().getId()));
    }

    private org.avni.server.service.abha.AbhaEventRequest verifiedAbhaRequest(String abhaNumber) {
        org.avni.server.service.abha.AbhaEventRequest request = new org.avni.server.service.abha.AbhaEventRequest();
        request.setSubjectUUID(patient.getUuid());
        request.setAbhaNumber(abhaNumber);
        request.setEkaOid("oid-verified-1");
        return request;
    }

    // The hooks' after-advice runs after @Messageable's, so messaging sees each save as it did before the hooks.
    @Test
    public void theHooksRunAfterMessageable() {
        List<String> subjectChain = aspects(individualService);
        assertTrue(subjectChain.toString(), subjectChain.indexOf("abhaLinkAspect") >= 0);
        assertTrue(subjectChain.toString(), subjectChain.indexOf("abhaLinkAspect") < subjectChain.indexOf("messageableAnnotationAspect"));
        for (Object visitService : List.of(encounterService, programEncounterService)) {
            List<String> visitChain = aspects(visitService);
            assertTrue(visitChain.toString(), visitChain.indexOf("abdmCareContextAspect") >= 0);
            assertTrue(visitChain.toString(), visitChain.indexOf("abdmCareContextAspect") < visitChain.indexOf("messageableAnnotationAspect"));
        }
    }

    // Outermost first.
    private static List<String> aspects(Object proxy) {
        List<String> names = new ArrayList<>();
        for (Advisor advisor : ((Advised) proxy).getAdvisors()) {
            if (advisor instanceof AspectJPrecedenceInformation info) names.add(info.getAspectName());
        }
        return names;
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private Encounter completedVisit() {
        Encounter visit = new Encounter();
        visit.assignUUID();
        visit.setEncounterType(encounterType);
        visit.setIndividual(patient);
        visit.setEncounterDateTime(DateTime.now().minusHours(1), organisation.getUser());
        visit.setObservations(new ObservationCollection());
        visit.setCancelObservations(new ObservationCollection());
        return visit;
    }

    private ProgramEncounter completedProgramVisit() {
        ProgramEncounter visit = new ProgramEncounter();
        visit.assignUUID();
        visit.setEncounterType(programEncounterType);
        visit.setProgramEnrolment(enrolment);
        visit.setEncounterDateTime(DateTime.now().minusHours(1), organisation.getUser());
        visit.setObservations(new ObservationCollection());
        visit.setCancelObservations(new ObservationCollection());
        return visit;
    }

    private ProgramEncounterRequest programVisitRequest() {
        ProgramEncounterRequest request = new ProgramEncounterRequest();
        request.setUuid(UUID.randomUUID().toString());
        request.setProgramEnrolmentUUID(enrolment.getUuid());
        request.setEncounterTypeUUID(programEncounterType.getUuid());
        request.setEncounterDateTime(DateTime.now().minusHours(1));
        request.setObservations(Collections.emptyList());
        request.setCancelObservations(Collections.emptyList());
        return request;
    }

    private Individual subjectWithAnAbha() {
        ObservationCollection observations = new ObservationCollection();
        observations.put(abhaNumber.getUuid(), ABHA_NUMBER);
        return new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).withObservations(observations).build();
    }

    // Eka settings that can share (a HIP ID, a record-sharing client, a system user and a signing key), so only the switches decide.
    private void saveEkaSettings() {
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.assignUUID();
        row.setSystemName(SystemName.Eka);
        row.setConfig(new JsonObject()
                .with(EkaSystemConfig.ENVIRONMENT, "sandbox")
                .with(EkaSystemConfig.CLIENT_ID, "client-1")
                .with(EkaSystemConfig.ENCRYPTED_CLIENT_SECRET, "encrypted-secret")
                .with(EkaSystemConfig.HIP_IDS, List.of(HIP_ID))
                .with(EkaSystemConfig.SHARING_CLIENT_ID, "sharing-client-1")
                .with(EkaSystemConfig.ENCRYPTED_SHARING_CLIENT_SECRET, "encrypted-sharing-secret")
                .with(EkaSystemConfig.AVNI_SYSTEM_USER, organisation.getUser().getUsername())
                .with(EkaSystemConfig.ENCRYPTED_WEBHOOK_SIGNING_KEY, "encrypted-signing-key"));
        externalSystemConfigRepository.save(row);
    }

    private void patientHoldsAnAbha() {
        asSuperAdmin(jdbc -> jdbc.update("insert into public.abha_link (uuid, organisation_id, individual_id, abha_number, created_by_id, " +
                        "last_modified_by_id, created_date_time, last_modified_date_time) values (?, ?, ?, '12345678568912', 1, 1, now(), now())",
                UUID.randomUUID().toString(), organisation.getOrganisationId(), patient.getId()));
    }

    private void switches(boolean abha, boolean recordSharing) {
        String settings = String.format("{\"enableAbha\": %s, \"enableAbdmRecordSharing\": %s}", abha, recordSharing);
        asSuperAdmin(jdbc -> jdbc.update("update public.organisation_config set settings = coalesce(settings, '{}'::jsonb) || ?::jsonb " +
                "where organisation_id = ?", settings, organisation.getOrganisationId()));
    }

    // "<reference> <hip id> <status>", in the order written.
    private List<String> careContexts() {
        List<String> rows = new ArrayList<>();
        asSuperAdmin(jdbc -> rows.addAll(jdbc.query("select reference, hip_id, status from public.abdm_care_context where organisation_id = ? order by id",
                (rs, i) -> rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3), organisation.getOrganisationId())));
        return rows;
    }

    private boolean saved(AbstractEncounter visit) {
        String table = visit instanceof ProgramEncounter ? "program_encounter" : "encounter";
        Integer[] count = new Integer[1];
        asSuperAdmin(jdbc -> count[0] = jdbc.queryForObject("select count(*) from public." + table + " where uuid = ?", Integer.class, visit.getUuid()));
        return count[0] == 1;
    }

    private Integer abhaLinks(Individual subject) {
        Integer[] count = new Integer[1];
        asSuperAdmin(jdbc -> count[0] = jdbc.queryForObject("select count(*) from public.abha_link l join public.individual i on i.id = l.individual_id " +
                "where i.uuid = ? and not l.is_voided", Integer.class, subject.getUuid()));
        return count[0];
    }

    private void asSuperAdmin(java.util.function.Consumer<JdbcTemplate> work) {
        setUser(userRepository.getDefaultSuperAdmin());
        try {
            work.accept(new JdbcTemplate(dataSource));
        } finally {
            setUser(organisation.getUser());
        }
    }
}
