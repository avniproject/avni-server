package org.avni.server.service;

import org.avni.server.dao.CardRepository;
import org.avni.server.dao.CustomCardConfigRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.OperationalEncounterTypeRepository;
import org.avni.server.dao.OperationalProgramRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.StandardReportCardTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.Account;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestAccountBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.reports.ReportCardBundleRequest;
import org.avni.server.web.request.reports.ReportCardWebRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CardServiceTest {
    private static final long ORG_ID = 920L;
    private static final String SUBJECT_TYPE_UUID = "subject-type-uuid";
    private static final String PROGRAM_UUID = "program-uuid";
    private static final String ENCOUNTER_TYPE_UUID = "encounter-type-uuid";

    @Mock private CardRepository cardRepository;
    @Mock private StandardReportCardTypeRepository standardReportCardTypeRepository;
    @Mock private SubjectTypeRepository subjectTypeRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private EncounterTypeRepository encounterTypeRepository;
    @Mock private CustomCardConfigRepository customCardConfigRepository;
    @Mock private CustomCardConfigService customCardConfigService;
    @Mock private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Mock private OperationalProgramRepository operationalProgramRepository;
    @Mock private OperationalEncounterTypeRepository operationalEncounterTypeRepository;

    private CardService cardService;
    private SubjectType subjectType;
    private Program program;
    private EncounterType encounterType;

    @Before
    public void setUp() {
        initMocks(this);
        Account account = new TestAccountBuilder().withRegion("IN").build();
        Organisation organisation = new TestOrganisationBuilder().withAccount(account).setId(ORG_ID).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);

        cardService = new CardService(cardRepository, standardReportCardTypeRepository, subjectTypeRepository,
                programRepository, encounterTypeRepository, customCardConfigRepository, customCardConfigService,
                operationalSubjectTypeRepository, operationalProgramRepository, operationalEncounterTypeRepository);

        subjectType = new SubjectType();
        subjectType.setUuid(SUBJECT_TYPE_UUID);
        subjectType.setName("Individual");

        program = new Program();
        program.setUuid(PROGRAM_UUID);
        program.setName("ANC");

        encounterType = new EncounterType();
        encounterType.setUuid(ENCOUNTER_TYPE_UUID);
        encounterType.setName("Avail Entitlement Follow-up");

        when(cardRepository.findByNameIgnoreCaseAndIsVoidedFalse(anyString())).thenReturn(Collections.emptyList());
    }

    @Test
    public void saveCard_rejects_encounter_type_without_operational_mapping_in_org() {
        when(encounterTypeRepository.findByUuid(ENCOUNTER_TYPE_UUID)).thenReturn(encounterType);
        when(operationalEncounterTypeRepository.findByEncounterTypeAndOrganisationId(encounterType, ORG_ID)).thenReturn(null);

        ReportCardWebRequest request = newWebRequest();
        request.setStandardReportCardInputEncounterTypes(Collections.singletonList(ENCOUNTER_TYPE_UUID));

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError for non-operational encounter type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(ENCOUNTER_TYPE_UUID));
            assertTrue(e.getMessage(), e.getMessage().contains("not available in this organisation"));
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void saveCard_rejects_subject_type_without_operational_mapping_in_org() {
        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(subjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(subjectType, ORG_ID)).thenReturn(null);

        ReportCardWebRequest request = newWebRequest();
        request.setStandardReportCardInputSubjectTypes(Collections.singletonList(SUBJECT_TYPE_UUID));

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError for non-operational subject type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(SUBJECT_TYPE_UUID));
        }
    }

    @Test
    public void saveCard_rejects_program_without_operational_mapping_in_org() {
        when(programRepository.findByUuid(PROGRAM_UUID)).thenReturn(program);
        when(operationalProgramRepository.findByProgramAndOrganisationId(program, ORG_ID)).thenReturn(null);

        ReportCardWebRequest request = newWebRequest();
        request.setStandardReportCardInputPrograms(Collections.singletonList(PROGRAM_UUID));

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError for non-operational program");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(PROGRAM_UUID));
        }
    }

    @Test
    public void saveCard_rejects_unknown_encounter_type_uuid() {
        when(encounterTypeRepository.findByUuid("does-not-exist")).thenReturn(null);

        ReportCardWebRequest request = newWebRequest();
        request.setStandardReportCardInputEncounterTypes(Collections.singletonList("does-not-exist"));

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError for unknown encounter type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("doesn't exist"));
        }
    }

    @Test
    public void saveCard_rejects_action_detail_program_without_operational_mapping() {
        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(subjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(subjectType, ORG_ID)).thenReturn(new OperationalSubjectType());
        when(programRepository.findByUuid(PROGRAM_UUID)).thenReturn(program);
        when(operationalProgramRepository.findByProgramAndOrganisationId(program, ORG_ID)).thenReturn(null);

        ReportCardWebRequest request = newWebRequest();
        request.setAction("DoVisit");
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        request.setActionDetailProgramUUID(PROGRAM_UUID);
        request.setActionDetailEncounterTypeUUID(ENCOUNTER_TYPE_UUID);
        request.setActionDetailVisitType("SOME_VISIT");

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError for non-operational DoVisit program");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(PROGRAM_UUID));
        }
    }

    @Test
    public void saveCard_rejects_action_detail_encounter_type_without_operational_mapping() {
        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(subjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(subjectType, ORG_ID)).thenReturn(new OperationalSubjectType());
        when(encounterTypeRepository.findByUuid(ENCOUNTER_TYPE_UUID)).thenReturn(encounterType);
        when(operationalEncounterTypeRepository.findByEncounterTypeAndOrganisationId(encounterType, ORG_ID)).thenReturn(null);

        ReportCardWebRequest request = newWebRequest();
        request.setAction("DoVisit");
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        request.setActionDetailEncounterTypeUUID(ENCOUNTER_TYPE_UUID);
        request.setActionDetailVisitType("SOME_VISIT");

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError for non-operational DoVisit encounter type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(ENCOUNTER_TYPE_UUID));
        }
    }

    @Test
    public void uploadCard_bundle_path_does_not_invoke_operational_validation() {
        ReportCardBundleRequest bundleRequest = new ReportCardBundleRequest();
        bundleRequest.setUuid("card-uuid");
        bundleRequest.setName("Bundle Card");
        bundleRequest.setColor("#fff");
        bundleRequest.setCount(1);
        bundleRequest.setStandardReportCardInputEncounterTypes(Collections.singletonList(ENCOUNTER_TYPE_UUID));
        when(cardRepository.findByUuid("card-uuid")).thenReturn(null);

        cardService.uploadCard(bundleRequest);

        verifyNoInteractions(operationalEncounterTypeRepository, operationalSubjectTypeRepository, operationalProgramRepository);
    }

    private ReportCardWebRequest newWebRequest() {
        ReportCardWebRequest request = new ReportCardWebRequest();
        request.setName("Some Report Card");
        request.setColor("#ffffff");
        request.setCount(1);
        return request;
    }
}
