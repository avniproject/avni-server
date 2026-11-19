package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.individualRelationship.IndividualRelationRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.individualRelationship.IndividualRelation;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.avni.server.domain.individualRelationship.IndividualRelationshipType;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.sync.MutableRequestFilter;
import org.avni.server.service.builder.*;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class SyncResponseStructureIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private TestGroupSubjectService testGroupSubjectService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private EncounterRepository encounterRepository;
    @Autowired
    private ProgramEncounterRepository programEncounterRepository;
    @Autowired
    private ChecklistDetailRepository checklistDetailRepository;
    @Autowired
    private ChecklistItemDetailRepository checklistItemDetailRepository;
    @Autowired
    private ChecklistRepository checklistRepository;
    @Autowired
    private ChecklistItemRepository checklistItemRepository;
    @Autowired
    private SubjectMigrationRepository subjectMigrationRepository;
    @Autowired
    private IndividualRelationRepository individualRelationRepository;
    @Autowired
    private IndividualRelationshipTypeRepository individualRelationshipTypeRepository;
    @Autowired
    private IndividualRelationshipRepository individualRelationshipRepository;
    @Autowired
    private ConceptRepository conceptRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private MutableRequestFilter mutableRequestFilter;
    @PersistenceContext
    private EntityManager entityManager;

    private static final List<String> ENCOUNTER_FIELDS = List.of("uuid", "createdDateTime", "lastModifiedDateTime", "version", "organisationId", "name",
            "earliestVisitDateTime", "maxVisitDateTime", "encounterDateTime", "observations", "cancelDateTime", "cancelObservations", "encounterLocation",
            "cancelLocation", "legacyId", "syncDisabled", "syncDisabledDateTime", "voided", "cancelled", "completed", "createdBy", "lastModifiedBy");
    private static final List<String> PROGRAM_ENROLMENT_FIELDS = List.of("uuid", "createdDateTime", "lastModifiedDateTime", "version", "organisationId",
            "enrolmentDateTime", "observations", "programExitDateTime", "enrolmentLocation", "exitLocation", "programExitObservations", "legacyId",
            "syncDisabled", "syncDisabledDateTime", "exited", "active", "voided", "createdBy", "lastModifiedBy");
    private static final List<String> CHECKLIST_FIELDS = List.of("uuid", "createdDateTime", "lastModifiedDateTime", "version", "organisationId",
            "baseDate", "syncDisabled", "syncDisabledDateTime", "voided", "createdBy", "lastModifiedBy");
    private static final List<String> CHECKLIST_ITEM_FIELDS = List.of("uuid", "createdDateTime", "lastModifiedDateTime", "version", "organisationId",
            "completionDate", "observations", "syncDisabled", "syncDisabledDateTime", "voided", "createdBy", "lastModifiedBy");
    private static final List<String> SUBJECT_MIGRATION_FIELDS = List.of("uuid", "createdDateTime", "lastModifiedDateTime", "version", "organisationId",
            "oldSyncConcept1Value", "newSyncConcept1Value", "oldSyncConcept2Value", "newSyncConcept2Value", "syncDisabled", "syncDisabledDateTime",
            "voided", "createdBy", "lastModifiedBy");
    private static final List<String> GROUP_SUBJECT_FIELDS = List.of("uuid", "createdDateTime", "lastModifiedDateTime", "version", "organisationId",
            "membershipStartDate", "membershipEndDate", "removalReasonConceptUUID", "syncDisabled", "syncDisabledDateTime", "groupSubjectUUID",
            "memberSubjectUUID", "groupRoleUUID", "voided", "createdBy", "lastModifiedBy");
    private static final List<String> INDIVIDUAL_RELATIONSHIP_FIELDS = List.of("uuid", "createdDateTime", "lastModifiedDateTime", "version", "organisationId",
            "enterDateTime", "exitDateTime", "exitObservations", "syncDisabled", "syncDisabledDateTime", "voided", "createdBy", "lastModifiedBy");

    private MockMvc syncMockMvc;
    private User user;
    private SubjectType subjectType;
    private SubjectType householdType;
    private EncounterType encounterType;
    private EncounterType programEncounterType;
    private Program program;
    private GroupRole groupRole;
    private ChecklistDetail checklistDetail;
    private ChecklistItemDetail checklistItemDetail;
    private AddressLevel location;
    private AddressLevel otherLocation;
    private Individual subject;
    private Individual otherSubject;
    private Individual household;
    private ProgramEnrolment enrolment;
    private Encounter encounter;
    private ProgramEncounter programEncounter;
    private Checklist checklist;
    private ChecklistItem checklistItem;
    private SubjectMigration subjectMigration;
    private GroupSubject groupSubject;
    private IndividualRelationship individualRelationship;
    private IndividualRelationshipType relationshipType;

    @Before
    public void setupData() throws Exception {
        syncMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(mutableRequestFilter).build();
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        Group group = organisationData.getGroup();
        group.setHasAllPrivileges(true);
        groupRepository.save(group);
        user = userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());

        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setType(Subject.Person).setName("Person").build());
        householdType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setGroup(true).setHousehold(true).setName("Household").build());
        FormMapping encounterFormMapping = testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("General Visit", subjectType);
        encounterType = encounterFormMapping.getEncounterType();
        program = testProgramService.addProgram(new ProgramBuilder().withName("Program").build(), subjectType);
        programEncounterType = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping("Program Visit", subjectType, program).getEncounterType();
        groupRole = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(householdType).withMemberSubjectType(subjectType).build());

        setUser(user.getUsername());

        location = catchmentData.getAddressLevel1();
        otherLocation = catchmentData.getAddressLevel2();
        subject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(location).build());
        otherSubject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(location).build());
        household = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(householdType).withLocation(location).build());
        enrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(subject).setObservations(new ObservationCollection()).build());

        encounter = new Encounter();
        encounter.assignUUID();
        encounter.setEncounterType(encounterType);
        encounter.setIndividual(subject);
        encounter.setAddressId(location.getId());
        encounter.setEncounterDateTime(DateTime.now(), user);
        encounter.setObservations(new ObservationCollection());
        encounter = encounterRepository.save(encounter);

        programEncounter = new ProgramEncounter();
        programEncounter.assignUUID();
        programEncounter.setEncounterType(programEncounterType);
        programEncounter.setProgramEnrolment(enrolment);
        programEncounter.setIndividual(subject);
        programEncounter.setAddressId(location.getId());
        programEncounter.setEncounterDateTime(DateTime.now(), user);
        programEncounter.setObservations(new ObservationCollection());
        programEncounter = programEncounterRepository.save(programEncounter);

        checklistDetail = new ChecklistDetail();
        checklistDetail.assignUUID();
        checklistDetail.setName("Vaccination");
        checklistDetailRepository.save(checklistDetail);
        Concept vaccine = new Concept();
        vaccine.assignUUID();
        vaccine.setName("BCG");
        vaccine.setDataType(ConceptDataType.NA.name());
        conceptRepository.save(vaccine);
        checklistItemDetail = new ChecklistItemDetail();
        checklistItemDetail.assignUUID();
        checklistItemDetail.setChecklistDetail(checklistDetail);
        checklistItemDetail.setConcept(vaccine);
        checklistItemDetail.setForm(encounterFormMapping.getForm());
        checklistItemDetail.setChecklistItemStatus(new ChecklistItemStatus());
        checklistItemDetailRepository.save(checklistItemDetail);
        checklist = new Checklist();
        checklist.assignUUID();
        checklist.setChecklistDetail(checklistDetail);
        checklist.setProgramEnrolment(enrolment);
        checklist.setBaseDate(DateTime.now());
        checklist = checklistRepository.save(checklist);
        checklistItem = new ChecklistItem();
        checklistItem.assignUUID();
        checklistItem.setChecklist(checklist);
        checklistItem.setChecklistItemDetail(checklistItemDetail);
        checklistItem.setObservations(new ObservationCollection());
        checklistItem = checklistItemRepository.save(checklistItem);

        subjectMigration = new SubjectMigration();
        subjectMigration.assignUUID();
        subjectMigration.setIndividual(subject);
        subjectMigration.setSubjectType(subjectType);
        subjectMigration.setOldAddressLevel(location);
        subjectMigration.setNewAddressLevel(otherLocation);
        subjectMigration = subjectMigrationRepository.save(subjectMigration);

        groupSubject = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRole).withGroup(household).withMember(subject).build());

        IndividualRelation parent = saveRelation("Parent");
        IndividualRelation child = saveRelation("Child");
        relationshipType = new IndividualRelationshipType();
        relationshipType.assignUUID();
        relationshipType.setName("Parent-Child");
        relationshipType.setIndividualAIsToB(parent);
        relationshipType.setIndividualBIsToA(child);
        individualRelationshipTypeRepository.save(relationshipType);
        individualRelationship = new IndividualRelationship();
        individualRelationship.assignUUID();
        individualRelationship.setRelationship(relationshipType);
        individualRelationship.setIndividuala(subject);
        individualRelationship.setIndividualB(otherSubject);
        individualRelationship.setEnterDateTime(DateTime.now());
        individualRelationship = individualRelationshipRepository.save(individualRelationship);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    public void encounterSyncResponseIsUnchanged() throws Exception {
        JsonNode encounterNode = onlyEmbedded(syncResponse("/encounter/v2", "encounterTypeUuid", encounterType.getUuid()), "encounter");

        assertFields(encounterNode, ENCOUNTER_FIELDS);
        assertLinks(encounterNode, withAuditAndFilledByLinks(links("encounterTypeUUID", encounterType.getUuid(), "individualUUID", subject.getUuid())));
    }

    @Test
    public void programEnrolmentSyncResponseIsUnchanged() throws Exception {
        JsonNode enrolmentNode = onlyEmbedded(syncResponse("/programEnrolment/v2", "programUuid", program.getUuid()), "programEnrolment");

        assertFields(enrolmentNode, PROGRAM_ENROLMENT_FIELDS);
        assertLinks(enrolmentNode, withAuditLinks(links("programUUID", program.getUuid(), "individualUUID", subject.getUuid())));
    }

    @Test
    public void programEncounterSyncResponseIsUnchanged() throws Exception {
        JsonNode programEncounterNode = onlyEmbedded(syncResponse("/programEncounter/v2", "programEncounterTypeUuid", programEncounterType.getUuid()), "programEncounter");

        assertFields(programEncounterNode, ENCOUNTER_FIELDS);
        assertLinks(programEncounterNode, withAuditAndFilledByLinks(links("encounterTypeUUID", programEncounterType.getUuid(), "programEnrolmentUUID", enrolment.getUuid())));
    }

    @Test
    public void checklistSyncResponseIsUnchanged() throws Exception {
        JsonNode checklistNode = onlyEmbedded(syncResponse("/txNewChecklistEntity/v2", "checklistDetailUuid", checklistDetail.getUuid()), "txNewChecklistEntity");

        assertFields(checklistNode, CHECKLIST_FIELDS);
        assertLinks(checklistNode, withAuditLinks(links("programEnrolmentUUID", enrolment.getUuid(), "checklistDetailUUID", checklistDetail.getUuid())));
    }

    @Test
    public void checklistItemSyncResponseIsUnchanged() throws Exception {
        JsonNode checklistItemNode = onlyEmbedded(syncResponse("/txNewChecklistItemEntity/v2", "checklistDetailUuid", checklistDetail.getUuid()), "txNewChecklistItemEntity");

        assertFields(checklistItemNode, CHECKLIST_ITEM_FIELDS);
        assertLinks(checklistItemNode, withAuditLinks(links("checklistUUID", checklist.getUuid(), "checklistItemDetailUUID", checklistItemDetail.getUuid())));
    }

    @Test
    public void subjectMigrationSyncResponseIsUnchanged() throws Exception {
        JsonNode subjectMigrationNode = onlyEmbedded(syncResponse("/subjectMigrations/v2", "subjectTypeUuid", subjectType.getUuid()), "subjectMigrations");

        assertFields(subjectMigrationNode, SUBJECT_MIGRATION_FIELDS);
        assertLinks(subjectMigrationNode, withAuditLinks(links("individualUUID", subject.getUuid(), "subjectTypeUUID", subjectType.getUuid(),
                "oldAddressLevelUUID", location.getUuid(), "newAddressLevelUUID", otherLocation.getUuid())));
    }

    @Test
    public void groupSubjectSyncResponseIsUnchanged() throws Exception {
        JsonNode groupSubjectNode = onlyEmbedded(syncResponse("/groupSubject/v2", "subjectTypeUuid", householdType.getUuid()), "groupSubject");

        assertFields(groupSubjectNode, GROUP_SUBJECT_FIELDS);
        assertGroupSubjectUuids(groupSubjectNode);
        assertLinks(groupSubjectNode, withAuditLinks(links("groupSubjectUUID", household.getUuid(), "memberSubjectUUID", subject.getUuid(), "groupRoleUUID", groupRole.getUuid())));
    }

    @Test
    public void individualRelationshipSyncResponseIsUnchanged() throws Exception {
        JsonNode individualRelationshipNode = onlyEmbedded(syncResponse("/individualRelationship/v2", "subjectTypeUuid", subjectType.getUuid()), "individualRelationship");

        assertFields(individualRelationshipNode, INDIVIDUAL_RELATIONSHIP_FIELDS);
        assertLinks(individualRelationshipNode, withAuditLinks(links("relationshipTypeUUID", relationshipType.getUuid(), "individualAUUID", subject.getUuid(), "individualBUUID", otherSubject.getUuid())));
    }

    @Test
    public void subjectAllEntitiesResponseIsUnchanged() throws Exception {
        JsonNode response = mapper.readTree(syncMockMvc.perform(get("/subject/" + subject.getUuid() + "/allEntities")).andReturn().getResponse().getContentAsString());

        assertEquals(Set.of("individual", "encounters", "programEnrolments", "programEncounters", "checklists", "checklistItems", "groupSubjects", "individualRelationships"), fieldNames(response));
        JsonNode encounterNode = only(response, "encounters");
        assertFields(encounterNode, ENCOUNTER_FIELDS);
        assertLinks(encounterNode, links("encounterTypeUUID", encounterType.getUuid(), "individualUUID", subject.getUuid()));
        JsonNode enrolmentNode = only(response, "programEnrolments");
        assertFields(enrolmentNode, PROGRAM_ENROLMENT_FIELDS);
        assertLinks(enrolmentNode, links("programUUID", program.getUuid(), "individualUUID", subject.getUuid()));
        JsonNode programEncounterNode = only(response, "programEncounters");
        assertFields(programEncounterNode, ENCOUNTER_FIELDS);
        assertLinks(programEncounterNode, links("encounterTypeUUID", programEncounterType.getUuid(), "programEnrolmentUUID", enrolment.getUuid()));
        JsonNode checklistNode = only(response, "checklists");
        assertFields(checklistNode, CHECKLIST_FIELDS);
        assertLinks(checklistNode, links("programEnrolmentUUID", enrolment.getUuid(), "checklistDetailUUID", checklistDetail.getUuid()));
        JsonNode checklistItemNode = only(response, "checklistItems");
        assertFields(checklistItemNode, CHECKLIST_ITEM_FIELDS);
        assertLinks(checklistItemNode, links("checklistUUID", checklist.getUuid(), "checklistItemDetailUUID", checklistItemDetail.getUuid()));
        JsonNode groupSubjectNode = only(response, "groupSubjects");
        assertFields(groupSubjectNode, GROUP_SUBJECT_FIELDS);
        assertGroupSubjectUuids(groupSubjectNode);
        assertLinks(groupSubjectNode, links("groupSubjectUUID", household.getUuid(), "memberSubjectUUID", subject.getUuid(), "groupRoleUUID", groupRole.getUuid()));
        JsonNode individualRelationshipNode = only(response, "individualRelationships");
        assertFields(individualRelationshipNode, INDIVIDUAL_RELATIONSHIP_FIELDS);
        assertLinks(individualRelationshipNode, links("relationshipTypeUUID", relationshipType.getUuid(), "individualAUUID", subject.getUuid(), "individualBUUID", otherSubject.getUuid()));
    }

    private JsonNode syncResponse(String path, String typeParameter, String typeUuid) throws Exception {
        String body = syncMockMvc.perform(get(path)
                        .param("lastModifiedDateTime", "1900-01-01T00:00:00.000Z")
                        .param("now", DateTime.now().plusDays(1).toString())
                        .param(typeParameter, typeUuid)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private JsonNode onlyEmbedded(JsonNode response, String embeddedName) {
        assertEquals(Set.of("_embedded", "slice"), fieldNames(response));
        assertEquals(Set.of(embeddedName), fieldNames(response.get("_embedded")));
        return only(response.get("_embedded"), embeddedName);
    }

    private JsonNode only(JsonNode parent, String arrayName) {
        JsonNode array = parent.get(arrayName);
        assertEquals(arrayName, 1, array.size());
        return array.get(0);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private void assertFields(JsonNode entity, List<String> expectedFields) {
        Set<String> expected = new TreeSet<>(expectedFields);
        expected.add("_links");
        assertEquals(expected, new TreeSet<>(fieldNames(entity)));
    }

    private void assertLinks(JsonNode entity, LinkedHashMap<String, String> expectedLinks) {
        LinkedHashMap<String, String> actualLinks = new LinkedHashMap<>();
        entity.get("_links").fields().forEachRemaining(link -> actualLinks.put(link.getKey(), link.getValue().get("href").asText()));
        assertEquals(new ArrayList<>(expectedLinks.entrySet()), new ArrayList<>(actualLinks.entrySet()));
    }

    private void assertGroupSubjectUuids(JsonNode groupSubjectNode) {
        assertEquals(household.getUuid(), groupSubjectNode.get("groupSubjectUUID").asText());
        assertEquals(subject.getUuid(), groupSubjectNode.get("memberSubjectUUID").asText());
        assertEquals(groupRole.getUuid(), groupSubjectNode.get("groupRoleUUID").asText());
    }

    private LinkedHashMap<String, String> links(String... relsAndHrefs) {
        LinkedHashMap<String, String> links = new LinkedHashMap<>();
        for (int i = 0; i < relsAndHrefs.length; i += 2) links.put(relsAndHrefs[i], relsAndHrefs[i + 1]);
        return links;
    }

    private LinkedHashMap<String, String> withAuditLinks(LinkedHashMap<String, String> links) {
        links.putAll(links("createdByUUID", user.getUuid(), "createdBy", user.getName(), "lastModifiedByUUID", user.getUuid(), "lastModifiedBy", user.getName()));
        return links;
    }

    private LinkedHashMap<String, String> withAuditAndFilledByLinks(LinkedHashMap<String, String> links) {
        withAuditLinks(links).putAll(links("filledByUUID", user.getUuid(), "filledBy", user.getName()));
        return links;
    }

    private IndividualRelation saveRelation(String name) {
        IndividualRelation relation = new IndividualRelation();
        relation.assignUUID();
        relation.setName(name);
        return individualRelationRepository.save(relation);
    }
}
