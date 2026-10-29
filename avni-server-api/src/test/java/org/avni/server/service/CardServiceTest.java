package org.avni.server.service;

import org.avni.server.dao.CardRepository;
import org.avni.server.dao.CustomCardConfigRepository;
import org.avni.server.dao.DashboardRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.OperationalEncounterTypeRepository;
import org.avni.server.dao.OperationalProgramRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.StandardReportCardTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.domain.Account;
import org.avni.server.domain.CustomCardConfig;
import org.avni.server.domain.Dashboard;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.OperationalEncounterType;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.domain.factory.TestAccountBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.domain.metadata.AttendanceTypeBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.BadRequestError;
import org.avni.server.domain.ReportCard;
import org.avni.server.domain.ReportCardAction;
import org.avni.server.web.request.CustomCardConfigRequest;
import org.avni.server.web.request.reports.ReportCardBundleRequest;
import org.avni.server.web.request.reports.ReportCardWebRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CardServiceTest {
    private static final long ORG_ID = 920L;
    private static final String SUBJECT_TYPE_UUID = "subject-type-uuid";
    private static final String PROGRAM_UUID = "program-uuid";
    private static final String ENCOUNTER_TYPE_UUID = "encounter-type-uuid";
    private static final String DASHBOARD_UUID = "dashboard-uuid";

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
    @Mock private AttendanceTypeRepository attendanceTypeRepository;
    @Mock private DashboardRepository dashboardRepository;

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
                operationalSubjectTypeRepository, operationalProgramRepository, operationalEncounterTypeRepository,
                attendanceTypeRepository, dashboardRepository);

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
    public void saveCard_rejects_mark_attendance_when_subject_type_is_not_group() {
        SubjectType individualSubjectType = newGroupSubjectType(SUBJECT_TYPE_UUID, "Beneficiary", false, true);
        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(individualSubjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(individualSubjectType, ORG_ID)).thenReturn(new OperationalSubjectType());

        ReportCardWebRequest request = newWebRequest();
        request.setAction("MarkAttendance");
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        request.setActionDetailAttendanceTypeUUID("attendance-type-uuid");

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError for non-Group subject type on MarkAttendance");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not a Group subject type"));
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void saveCard_rejects_mark_attendance_when_attendance_not_enabled_on_subject_type() {
        SubjectType groupWithoutAttendance = newGroupSubjectType(SUBJECT_TYPE_UUID, "Class", true, false);
        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(groupWithoutAttendance);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(groupWithoutAttendance, ORG_ID)).thenReturn(new OperationalSubjectType());

        ReportCardWebRequest request = newWebRequest();
        request.setAction("MarkAttendance");
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        request.setActionDetailAttendanceTypeUUID("attendance-type-uuid");

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError when attendance is not enabled on subject type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("does not have attendance enabled"));
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void saveCard_rejects_mark_attendance_when_attendance_type_belongs_to_other_subject_type() {
        SubjectType groupSubjectType = newGroupSubjectType(SUBJECT_TYPE_UUID, "Class", true, true);
        SubjectType otherSubjectType = newGroupSubjectType("other-st-uuid", "OtherClass", true, true);
        AttendanceType attendanceType = newAttendanceType("attendance-type-uuid", "Morning Prayer", otherSubjectType);

        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(groupSubjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(groupSubjectType, ORG_ID)).thenReturn(new OperationalSubjectType());
        when(attendanceTypeRepository.findByUuid("attendance-type-uuid")).thenReturn(attendanceType);

        ReportCardWebRequest request = newWebRequest();
        request.setAction("MarkAttendance");
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        request.setActionDetailAttendanceTypeUUID("attendance-type-uuid");

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError when attendance type belongs to another subject type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("does not belong to"));
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void saveCard_rejects_mark_attendance_without_attendance_type_uuid() {
        SubjectType groupSubjectType = newGroupSubjectType(SUBJECT_TYPE_UUID, "Class", true, true);
        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(groupSubjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(groupSubjectType, ORG_ID)).thenReturn(new OperationalSubjectType());

        ReportCardWebRequest request = newWebRequest();
        request.setAction("MarkAttendance");
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        // No attendanceTypeUUID

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError when attendance type UUID is missing");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Attendance type is required"));
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void saveCard_persists_mark_attendance_card_when_everything_valid() {
        SubjectType groupSubjectType = newGroupSubjectType(SUBJECT_TYPE_UUID, "Class", true, true);
        AttendanceType attendanceType = newAttendanceType("attendance-type-uuid", "Morning Prayer", groupSubjectType);

        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(groupSubjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(groupSubjectType, ORG_ID)).thenReturn(new OperationalSubjectType());
        when(attendanceTypeRepository.findByUuid("attendance-type-uuid")).thenReturn(attendanceType);

        ReportCardWebRequest request = newWebRequest();
        request.setAction("MarkAttendance");
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        request.setActionDetailAttendanceTypeUUID("attendance-type-uuid");

        cardService.saveCard(request);

        verify(cardRepository).save(any());
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

    @Test
    public void editCard_voids_orphaned_custom_card_config_when_card_switched_away_from_custom_design() {
        CustomCardConfig existingConfig = new CustomCardConfig();
        existingConfig.setId(42L);
        existingConfig.assignUUID();
        existingConfig.setName("sample");

        ReportCard existingCard = new ReportCard();
        existingCard.setName("Some Report Card");
        existingCard.setCustomCardConfig(existingConfig);

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Request carries no customCardConfig and no customCardConfigUUID, i.e.
        // the card has been switched from Custom Design to a Standard card.
        ReportCardWebRequest request = newWebRequest();

        cardService.editCard(request, 7L);

        assertNull(existingCard.getCustomCardConfig());
        assertTrue(existingConfig.isVoided());
        assertFalse("orphaned config name must be freed up", "sample".equals(existingConfig.getName()));
        verify(customCardConfigRepository).save(existingConfig);
    }

    @Test
    public void editCard_does_not_void_config_when_another_card_still_references_it() {
        CustomCardConfig existingConfig = new CustomCardConfig();
        existingConfig.setId(42L);
        existingConfig.assignUUID();
        existingConfig.setName("sample");

        ReportCard existingCard = new ReportCard();
        existingCard.setId(7L);
        existingCard.setName("Some Report Card");
        existingCard.setCustomCardConfig(existingConfig);

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardRepository.existsByCustomCardConfigIdAndIdNotAndIsVoidedFalse(42L, 7L)).thenReturn(true);

        // Switch this card away from custom design, but another card still uses the config.
        ReportCardWebRequest request = newWebRequest();

        cardService.editCard(request, 7L);

        assertNull(existingCard.getCustomCardConfig());
        assertFalse(existingConfig.isVoided());
        assertEquals("sample", existingConfig.getName());
        verify(customCardConfigRepository, never()).save(any());
    }

    @Test
    public void uploadCard_voids_orphaned_custom_card_config_when_bundle_card_switched_away_from_custom_design() {
        CustomCardConfig existingConfig = new CustomCardConfig();
        existingConfig.setId(42L);
        existingConfig.assignUUID();
        existingConfig.setName("sample");

        ReportCard existingCard = new ReportCard();
        existingCard.setId(7L);
        existingCard.setName("Bundle Card");
        existingCard.setCustomCardConfig(existingConfig);

        when(cardRepository.findByUuid("card-uuid")).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReportCardBundleRequest bundleRequest = new ReportCardBundleRequest();
        bundleRequest.setUuid("card-uuid");
        bundleRequest.setName("Bundle Card");
        bundleRequest.setColor("#fff");
        bundleRequest.setCount(1);
        // No customCardConfig on the bundle: card switched away from custom design.

        cardService.uploadCard(bundleRequest);

        assertNull(existingCard.getCustomCardConfig());
        assertTrue(existingConfig.isVoided());
        assertFalse("orphaned config name must be freed up", "sample".equals(existingConfig.getName()));
        verify(customCardConfigRepository).save(existingConfig);
    }

    @Test
    public void editCard_does_not_void_config_when_card_remains_custom_design_with_same_config() {
        CustomCardConfig existingConfig = new CustomCardConfig();
        existingConfig.setId(42L);
        existingConfig.assignUUID();
        existingConfig.setName("sample");

        ReportCard existingCard = new ReportCard();
        existingCard.setName("Some Report Card");
        existingCard.setCustomCardConfig(existingConfig);

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(customCardConfigService.createOrUpdateCustomCardConfig(any())).thenReturn(existingConfig);

        ReportCardWebRequest request = newWebRequest();
        CustomCardConfigRequest configRequest = new CustomCardConfigRequest();
        configRequest.setUuid(existingConfig.getUuid());
        configRequest.setName("sample");
        request.setCustomCardConfig(configRequest);

        cardService.editCard(request, 7L);

        assertEquals(existingConfig, existingCard.getCustomCardConfig());
        assertFalse(existingConfig.isVoided());
        assertEquals("sample", existingConfig.getName());
        verify(customCardConfigRepository, never()).save(any());
    }

    @Test
    public void uploadCard_stores_open_dashboard_target_in_action_detail() {
        when(cardRepository.findByUuid("card-uuid")).thenReturn(null);

        ReportCardBundleRequest bundleRequest = newBundleRequest();
        bundleRequest.setAction("OpenDashboard");
        bundleRequest.setActionDetailDashboardUUID(DASHBOARD_UUID);

        cardService.uploadCard(bundleRequest);

        ArgumentCaptor<ReportCard> savedCard = ArgumentCaptor.forClass(ReportCard.class);
        verify(cardRepository).save(savedCard.capture());
        assertEquals(ReportCardAction.OpenDashboard, savedCard.getValue().getAction());
        assertEquals(DASHBOARD_UUID, savedCard.getValue().getActionDetailDashboardUUID());
        assertEquals(Set.of("dashboardUUID"), savedCard.getValue().getActionDetail().keySet());
    }

    @Test
    public void saveCard_rejects_open_dashboard_without_target() {
        ReportCardWebRequest request = newWebRequest();
        request.setAction("OpenDashboard");
        // No dashboardUUID

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError when dashboard UUID is missing");
        } catch (BadRequestError e) {
            assertEquals("Dashboard is required when action is OpenDashboard", e.getMessage());
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void saveCard_rejects_open_dashboard_with_blank_target() {
        ReportCardWebRequest request = newWebRequest();
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID("   ");

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError when dashboard UUID is blank");
        } catch (BadRequestError e) {
            assertEquals("Dashboard is required when action is OpenDashboard", e.getMessage());
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void uploadCard_rejects_open_dashboard_without_target() {
        when(cardRepository.findByUuid("card-uuid")).thenReturn(null);

        ReportCardBundleRequest bundleRequest = newBundleRequest();
        bundleRequest.setAction("OpenDashboard");
        // No dashboardUUID

        try {
            cardService.uploadCard(bundleRequest);
            fail("Expected BadRequestError when dashboard UUID is missing");
        } catch (BadRequestError e) {
            assertEquals("Dashboard is required when action is OpenDashboard", e.getMessage());
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void editCard_clears_open_dashboard_target_when_action_changes_to_view_subject_profile() {
        ReportCard existingCard = new ReportCard();
        existingCard.setName("Some Report Card");
        existingCard.setAction(ReportCardAction.OpenDashboard);
        existingCard.setActionDetailDashboardUUID(DASHBOARD_UUID);

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // The request carries the old target together with the new action. The web app sends every
        // action field it has, whatever the action is, and avniproject/avni-webapp#1830 adds
        // actionDetailDashboardUUID to those fields.
        ReportCardWebRequest request = newWebRequest();
        request.setAction("ViewSubjectProfile");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);

        cardService.editCard(request, 7L);

        assertNull(existingCard.getActionDetail());
        assertNull(existingCard.getActionDetailDashboardUUID());
    }

    @Test
    public void editCard_clears_open_dashboard_target_when_action_is_removed() {
        ReportCard existingCard = new ReportCard();
        existingCard.setName("Some Report Card");
        existingCard.setAction(ReportCardAction.OpenDashboard);
        existingCard.setActionDetailDashboardUUID(DASHBOARD_UUID);

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // The request carries no action, and still carries the old target.
        ReportCardWebRequest request = newWebRequest();
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);

        cardService.editCard(request, 7L);

        assertNull(existingCard.getAction());
        assertNull(existingCard.getActionDetail());
        assertNull(existingCard.getActionDetailDashboardUUID());
    }

    @Test
    public void uploadCard_clears_open_dashboard_target_when_bundle_card_changes_action() {
        ReportCard existingCard = new ReportCard();
        existingCard.setName("Bundle Card");
        existingCard.setAction(ReportCardAction.OpenDashboard);
        existingCard.setActionDetailDashboardUUID(DASHBOARD_UUID);

        when(cardRepository.findByUuid("card-uuid")).thenReturn(existingCard);

        // Only a hand-edited bundle carries the old target together with the new action: an exported one
        // never does, because ReportCardMapper.toBundle reads the target from actionDetail, which holds
        // a dashboardUUID only when the action is OpenDashboard. This test guards that case.
        ReportCardBundleRequest bundleRequest = newBundleRequest();
        bundleRequest.setAction("ViewSubjectProfile");
        bundleRequest.setActionDetailDashboardUUID(DASHBOARD_UUID);

        cardService.uploadCard(bundleRequest);

        assertNull(existingCard.getActionDetail());
        assertNull(existingCard.getActionDetailDashboardUUID());
    }

    @Test
    public void saveCard_persists_open_dashboard_card_with_its_target() {
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(newDashboard(false));

        // A query makes this a Custom Data Card.
        ReportCardWebRequest request = newWebRequest();
        request.setQuery("({params}) => []");
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);

        ReportCard savedCard = cardService.saveCard(request);

        assertEquals("({params}) => []", savedCard.getQuery());
        assertEquals(ReportCardAction.OpenDashboard, savedCard.getAction());
        assertEquals(DASHBOARD_UUID, savedCard.getActionDetailDashboardUUID());
        verify(dashboardRepository).findByUuid(DASHBOARD_UUID);
        verify(cardRepository).save(savedCard);
    }

    @Test
    public void saveCard_persists_open_dashboard_action_on_custom_design_card() {
        CustomCardConfig config = new CustomCardConfig();
        config.assignUUID();
        config.setName("sample");
        when(customCardConfigService.createOrUpdateCustomCardConfig(any())).thenReturn(config);
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(newDashboard(false));

        ReportCardWebRequest request = newWebRequest();
        CustomCardConfigRequest configRequest = new CustomCardConfigRequest();
        configRequest.setName("sample");
        request.setCustomCardConfig(configRequest);
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);

        ReportCard savedCard = cardService.saveCard(request);

        assertEquals(config, savedCard.getCustomCardConfig());
        assertEquals(ReportCardAction.OpenDashboard, savedCard.getAction());
        assertEquals(DASHBOARD_UUID, savedCard.getActionDetailDashboardUUID());
        verify(cardRepository).save(savedCard);
    }

    @Test
    public void saveCard_rejects_open_dashboard_when_target_dashboard_does_not_exist() {
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(null);

        ReportCardWebRequest request = newWebRequest();
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError when the target dashboard does not exist");
        } catch (BadRequestError e) {
            assertEquals("Dashboard with uuid dashboard-uuid doesn't exist", e.getMessage());
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void saveCard_rejects_open_dashboard_when_target_dashboard_is_voided() {
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(newDashboard(true));

        ReportCardWebRequest request = newWebRequest();
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);

        try {
            cardService.saveCard(request);
            fail("Expected BadRequestError when the target dashboard is voided");
        } catch (BadRequestError e) {
            assertEquals("Dashboard with uuid dashboard-uuid doesn't exist", e.getMessage());
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void editCard_rejects_open_dashboard_when_target_dashboard_is_voided() {
        // The card already points at a dashboard that has since been deleted.
        ReportCard existingCard = new ReportCard();
        existingCard.setName("Some Report Card");
        existingCard.setAction(ReportCardAction.OpenDashboard);
        existingCard.setActionDetailDashboardUUID(DASHBOARD_UUID);

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(newDashboard(true));

        // Saving it again with the same target is refused until the target changes.
        ReportCardWebRequest request = newWebRequest();
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);

        try {
            cardService.editCard(request, 7L);
            fail("Expected BadRequestError when the target dashboard is voided");
        } catch (BadRequestError e) {
            assertEquals("Dashboard with uuid dashboard-uuid doesn't exist", e.getMessage());
        }
        verify(cardRepository, never()).save(any());
    }

    @Test
    public void uploadCard_does_not_look_up_open_dashboard_target() {
        // A bundle imports cards before dashboards, so the target may not exist yet.
        when(cardRepository.findByUuid("card-uuid")).thenReturn(null);

        ReportCardBundleRequest bundleRequest = newBundleRequest();
        bundleRequest.setAction("OpenDashboard");
        bundleRequest.setActionDetailDashboardUUID(DASHBOARD_UUID);

        cardService.uploadCard(bundleRequest);

        ArgumentCaptor<ReportCard> savedCard = ArgumentCaptor.forClass(ReportCard.class);
        verify(cardRepository).save(savedCard.capture());
        assertEquals(DASHBOARD_UUID, savedCard.getValue().getActionDetailDashboardUUID());
        verifyNoInteractions(dashboardRepository);
    }

    @Test
    public void saveCard_stores_only_the_dashboard_when_request_also_carries_do_visit_fields() {
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(newDashboard(false));

        // The web app sends every action field whatever the action is.
        ReportCardWebRequest request = newWebRequest();
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);
        request.setActionDetailSubjectTypeUUID("unknown-subject-type");

        ReportCard savedCard = cardService.saveCard(request);

        assertEquals(Set.of("dashboardUUID"), savedCard.getActionDetail().keySet());
        verify(cardRepository).save(savedCard);
        verifyNoInteractions(subjectTypeRepository, operationalSubjectTypeRepository);
    }

    @Test
    public void editCard_keeps_only_the_last_actions_detail_across_open_dashboard_do_visit_and_back() {
        ReportCard existingCard = new ReportCard();
        existingCard.setName("Some Report Card");

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(newDashboard(false));
        when(subjectTypeRepository.findByUuid(SUBJECT_TYPE_UUID)).thenReturn(subjectType);
        when(operationalSubjectTypeRepository.findBySubjectTypeAndOrganisationId(subjectType, ORG_ID)).thenReturn(new OperationalSubjectType());
        when(encounterTypeRepository.findByUuid(ENCOUNTER_TYPE_UUID)).thenReturn(encounterType);
        when(operationalEncounterTypeRepository.findByEncounterTypeAndOrganisationId(encounterType, ORG_ID)).thenReturn(new OperationalEncounterType());

        ReportCardWebRequest openDashboardRequest = newWebRequest();
        openDashboardRequest.setAction("OpenDashboard");
        openDashboardRequest.setActionDetailDashboardUUID(DASHBOARD_UUID);

        cardService.editCard(openDashboardRequest, 7L);

        assertEquals(Set.of("dashboardUUID"), existingCard.getActionDetail().keySet());

        ReportCardWebRequest doVisitRequest = newWebRequest();
        doVisitRequest.setAction("DoVisit");
        doVisitRequest.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        doVisitRequest.setActionDetailEncounterTypeUUID(ENCOUNTER_TYPE_UUID);
        doVisitRequest.setActionDetailVisitType("SOME_VISIT");

        cardService.editCard(doVisitRequest, 7L);

        assertEquals(Set.of("subjectTypeUUID", "programUUID", "encounterTypeUUID", "visitType"), existingCard.getActionDetail().keySet());

        cardService.editCard(openDashboardRequest, 7L);

        assertEquals(ReportCardAction.OpenDashboard, existingCard.getAction());
        assertEquals(DASHBOARD_UUID, existingCard.getActionDetailDashboardUUID());
        assertEquals(Set.of("dashboardUUID"), existingCard.getActionDetail().keySet());
        verify(cardRepository, times(3)).save(existingCard);
    }

    @Test
    public void editCard_replaces_mark_attendance_detail_with_open_dashboard_target() {
        ReportCard existingCard = new ReportCard();
        existingCard.setName("Some Report Card");
        existingCard.setAction(ReportCardAction.MarkAttendance);
        existingCard.setActionDetailFields(SUBJECT_TYPE_UUID, "attendance-type-uuid");

        when(cardRepository.findOne(7L)).thenReturn(existingCard);
        when(cardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dashboardRepository.findByUuid(DASHBOARD_UUID)).thenReturn(newDashboard(false));

        // The request still carries the old Mark attendance fields next to the new action.
        ReportCardWebRequest request = newWebRequest();
        request.setAction("OpenDashboard");
        request.setActionDetailDashboardUUID(DASHBOARD_UUID);
        request.setActionDetailSubjectTypeUUID(SUBJECT_TYPE_UUID);
        request.setActionDetailAttendanceTypeUUID("attendance-type-uuid");

        cardService.editCard(request, 7L);

        assertEquals(ReportCardAction.OpenDashboard, existingCard.getAction());
        assertEquals(DASHBOARD_UUID, existingCard.getActionDetailDashboardUUID());
        assertEquals(Set.of("dashboardUUID"), existingCard.getActionDetail().keySet());
    }

    private ReportCardWebRequest newWebRequest() {
        ReportCardWebRequest request = new ReportCardWebRequest();
        request.setName("Some Report Card");
        request.setColor("#ffffff");
        request.setCount(1);
        return request;
    }

    private ReportCardBundleRequest newBundleRequest() {
        ReportCardBundleRequest request = new ReportCardBundleRequest();
        request.setUuid("card-uuid");
        request.setName("Bundle Card");
        request.setColor("#fff");
        request.setCount(1);
        return request;
    }

    private Dashboard newDashboard(boolean voided) {
        Dashboard dashboard = new Dashboard();
        dashboard.setUuid(DASHBOARD_UUID);
        dashboard.setName("Monthly Overview");
        dashboard.setVoided(voided);
        return dashboard;
    }

    private SubjectType newGroupSubjectType(String uuid, String name, boolean isGroup, boolean attendanceEnabled) {
        return new SubjectTypeBuilder()
                .setUuid(uuid)
                .setName(name)
                .setGroup(isGroup)
                .setAttendanceEnabled(attendanceEnabled)
                .build();
    }

    private AttendanceType newAttendanceType(String uuid, String name, SubjectType subjectType) {
        return new AttendanceTypeBuilder()
                .setUuid(uuid)
                .setName(name)
                .setSubjectType(subjectType)
                .build();
    }
}
