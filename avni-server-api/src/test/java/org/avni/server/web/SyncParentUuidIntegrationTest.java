package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.individualRelationship.IndividualRelationRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.individualRelationship.IndividualRelation;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.avni.server.domain.individualRelationship.IndividualRelationshipType;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.api.EncounterSearchRequest;
import org.hibernate.Hibernate;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class SyncParentUuidIntegrationTest extends AbstractControllerIntegrationTest {
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
    private ProgramEnrolmentRepository programEnrolmentRepository;
    @Autowired
    private ChecklistDetailRepository checklistDetailRepository;
    @Autowired
    private ChecklistRepository checklistRepository;
    @Autowired
    private ChecklistItemRepository checklistItemRepository;
    @Autowired
    private SubjectMigrationRepository subjectMigrationRepository;
    @Autowired
    private GroupSubjectRepository groupSubjectRepository;
    @Autowired
    private IndividualRelationRepository individualRelationRepository;
    @Autowired
    private IndividualRelationshipTypeRepository individualRelationshipTypeRepository;
    @Autowired
    private IndividualRelationshipRepository individualRelationshipRepository;
    @Autowired
    private EncounterSearchRepository encounterSearchRepository;
    @Autowired
    private EncounterController encounterController;
    @Autowired
    private ProgramEnrolmentController programEnrolmentController;
    @Autowired
    private ProgramEncounterController programEncounterController;
    @Autowired
    private ChecklistController checklistController;
    @Autowired
    private ChecklistItemController checklistItemController;
    @Autowired
    private SubjectMigrationController subjectMigrationController;
    @Autowired
    private GroupSubjectController groupSubjectController;
    @Autowired
    private IndividualRelationshipController individualRelationshipController;
    @PersistenceContext
    private EntityManager entityManager;

    private Individual subject;
    private Individual otherSubject;
    private Individual household;
    private ProgramEnrolment enrolment;
    private Checklist checklist;
    private String encounterUuid;
    private String programEncounterUuid;
    private String checklistItemUuid;
    private String subjectMigrationUuid;
    private String groupSubjectUuid;
    private String individualRelationshipUuid;

    @Before
    public void setupData() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        User user = organisationData.getUser();
        Group group = organisationData.getGroup();
        group.setHasAllPrivileges(true);
        groupRepository.save(group);

        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setType(Subject.Person).setName("Person").build());
        SubjectType householdType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setGroup(true).setHousehold(true).setName("Household").build());
        EncounterType encounterType = testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("General Visit", subjectType).getEncounterType();
        Program program = testProgramService.addProgram(new ProgramBuilder().withName("Program").build(), subjectType);
        EncounterType programEncounterType = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping("Program Visit", subjectType, program).getEncounterType();
        GroupRole groupRole = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withGroupSubjectType(householdType).withMemberSubjectType(subjectType).build());

        setUser(user.getUsername());

        AddressLevel location = catchmentData.getAddressLevel1();
        subject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(location).build());
        otherSubject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(location).build());
        household = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(householdType).withLocation(location).build());
        enrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(subject).setObservations(new ObservationCollection()).build());

        Encounter encounter = new Encounter();
        encounter.assignUUID();
        encounter.setEncounterType(encounterType);
        encounter.setIndividual(subject);
        encounter.setAddressId(location.getId());
        encounter.setEncounterDateTime(DateTime.now(), user);
        encounter.setObservations(new ObservationCollection());
        encounterUuid = encounterRepository.save(encounter).getUuid();

        ProgramEncounter programEncounter = new ProgramEncounter();
        programEncounter.assignUUID();
        programEncounter.setEncounterType(programEncounterType);
        programEncounter.setProgramEnrolment(enrolment);
        programEncounter.setIndividual(subject);
        programEncounter.setAddressId(location.getId());
        programEncounter.setEncounterDateTime(DateTime.now(), user);
        programEncounter.setObservations(new ObservationCollection());
        programEncounterUuid = programEncounterRepository.save(programEncounter).getUuid();

        ChecklistDetail checklistDetail = new ChecklistDetail();
        checklistDetail.assignUUID();
        checklistDetail.setName("Vaccination");
        checklistDetailRepository.save(checklistDetail);
        checklist = new Checklist();
        checklist.assignUUID();
        checklist.setChecklistDetail(checklistDetail);
        checklist.setProgramEnrolment(enrolment);
        checklist.setBaseDate(DateTime.now());
        checklistRepository.save(checklist);
        ChecklistItem checklistItem = new ChecklistItem();
        checklistItem.assignUUID();
        checklistItem.setChecklist(checklist);
        checklistItem.setObservations(new ObservationCollection());
        checklistItemUuid = checklistItemRepository.save(checklistItem).getUuid();

        SubjectMigration subjectMigration = new SubjectMigration();
        subjectMigration.assignUUID();
        subjectMigration.setIndividual(subject);
        subjectMigration.setSubjectType(subjectType);
        subjectMigration.setOldAddressLevel(location);
        subjectMigration.setNewAddressLevel(catchmentData.getAddressLevel2());
        subjectMigrationUuid = subjectMigrationRepository.save(subjectMigration).getUuid();

        groupSubjectUuid = testGroupSubjectService.save(new TestGroupSubjectBuilder().withGroupRole(groupRole).withGroup(household).withMember(subject).build()).getUuid();

        IndividualRelation parent = saveRelation("Parent");
        IndividualRelation child = saveRelation("Child");
        IndividualRelationshipType relationshipType = new IndividualRelationshipType();
        relationshipType.assignUUID();
        relationshipType.setName("Parent-Child");
        relationshipType.setIndividualAIsToB(parent);
        relationshipType.setIndividualBIsToA(child);
        individualRelationshipTypeRepository.save(relationshipType);
        IndividualRelationship individualRelationship = new IndividualRelationship();
        individualRelationship.assignUUID();
        individualRelationship.setRelationship(relationshipType);
        individualRelationship.setIndividuala(subject);
        individualRelationship.setIndividualB(otherSubject);
        individualRelationship.setEnterDateTime(DateTime.now());
        individualRelationshipUuid = individualRelationshipRepository.save(individualRelationship).getUuid();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    public void encounterLinksSubjectWithoutLoadingIt() {
        Encounter encounter = encounterRepository.findByUuid(encounterUuid);
        assertFalse(Hibernate.isInitialized(encounter.getIndividual()));

        EntityModel<Encounter> resource = encounterController.process(EntityModel.of(encounter));

        assertEquals(subject.getUuid(), resource.getRequiredLink("individualUUID").getHref());
        assertFalse(Hibernate.isInitialized(encounter.getIndividual()));
    }

    @Test
    public void programEnrolmentLinksSubjectWithoutLoadingIt() {
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(enrolment.getUuid());
        assertFalse(Hibernate.isInitialized(programEnrolment.getIndividual()));

        EntityModel<ProgramEnrolment> resource = programEnrolmentController.process(EntityModel.of(programEnrolment));

        assertEquals(subject.getUuid(), resource.getRequiredLink("individualUUID").getHref());
        assertFalse(Hibernate.isInitialized(programEnrolment.getIndividual()));
    }

    @Test
    public void programEncounterLinksEnrolmentWithoutLoadingIt() {
        ProgramEncounter programEncounter = programEncounterRepository.findByUuid(programEncounterUuid);
        assertFalse(Hibernate.isInitialized(programEncounter.getProgramEnrolment()));

        EntityModel<ProgramEncounter> resource = programEncounterController.process(EntityModel.of(programEncounter));

        assertEquals(enrolment.getUuid(), resource.getRequiredLink("programEnrolmentUUID").getHref());
        assertFalse(Hibernate.isInitialized(programEncounter.getProgramEnrolment()));
    }

    @Test
    public void checklistLinksEnrolmentWithoutLoadingIt() {
        Checklist loadedChecklist = checklistRepository.findByUuid(checklist.getUuid());
        assertFalse(Hibernate.isInitialized(loadedChecklist.getProgramEnrolment()));

        EntityModel<Checklist> resource = checklistController.process(EntityModel.of(loadedChecklist));

        assertEquals(enrolment.getUuid(), resource.getRequiredLink("programEnrolmentUUID").getHref());
        assertFalse(Hibernate.isInitialized(loadedChecklist.getProgramEnrolment()));
    }

    @Test
    public void checklistItemLinksChecklistWithoutLoadingIt() {
        ChecklistItem checklistItem = checklistItemRepository.findByUuid(checklistItemUuid);
        assertFalse(Hibernate.isInitialized(checklistItem.getChecklist()));

        EntityModel<ChecklistItem> resource = checklistItemController.process(EntityModel.of(checklistItem));

        assertEquals(checklist.getUuid(), resource.getRequiredLink("checklistUUID").getHref());
        assertFalse(Hibernate.isInitialized(checklistItem.getChecklist()));
    }

    @Test
    public void subjectMigrationLinksSubjectWithoutLoadingIt() {
        SubjectMigration subjectMigration = subjectMigrationRepository.findByUuid(subjectMigrationUuid);
        assertFalse(Hibernate.isInitialized(subjectMigration.getIndividual()));

        EntityModel<SubjectMigration> resource = subjectMigrationController.process(EntityModel.of(subjectMigration));

        assertEquals(subject.getUuid(), resource.getRequiredLink("individualUUID").getHref());
        assertFalse(Hibernate.isInitialized(subjectMigration.getIndividual()));
    }

    @Test
    public void groupSubjectLinksAndSerialisesGroupAndMemberWithoutLoadingThem() {
        GroupSubject groupSubject = groupSubjectRepository.findByUuid(groupSubjectUuid);
        assertFalse(Hibernate.isInitialized(groupSubject.getGroupSubject()));
        assertFalse(Hibernate.isInitialized(groupSubject.getMemberSubject()));

        EntityModel<GroupSubject> resource = groupSubjectController.process(EntityModel.of(groupSubject));
        JsonNode json = mapper.valueToTree(groupSubject);

        assertEquals(household.getUuid(), resource.getRequiredLink("groupSubjectUUID").getHref());
        assertEquals(subject.getUuid(), resource.getRequiredLink("memberSubjectUUID").getHref());
        assertEquals(household.getUuid(), json.get("groupSubjectUUID").asText());
        assertEquals(subject.getUuid(), json.get("memberSubjectUUID").asText());
        assertFalse(json.has("linkedGroupSubjectUuid"));
        assertFalse(json.has("linkedMemberSubjectUuid"));
        assertFalse(Hibernate.isInitialized(groupSubject.getGroupSubject()));
        assertFalse(Hibernate.isInitialized(groupSubject.getMemberSubject()));
    }

    @Test
    public void individualRelationshipLinksBothSubjectsWithoutLoadingThem() {
        IndividualRelationship individualRelationship = individualRelationshipRepository.findByUuid(individualRelationshipUuid);
        assertFalse(Hibernate.isInitialized(individualRelationship.getIndividuala()));
        assertFalse(Hibernate.isInitialized(individualRelationship.getIndividualB()));

        EntityModel<IndividualRelationship> resource = individualRelationshipController.process(EntityModel.of(individualRelationship));

        assertEquals(subject.getUuid(), resource.getRequiredLink("individualAUUID").getHref());
        assertEquals(otherSubject.getUuid(), resource.getRequiredLink("individualBUUID").getHref());
        assertFalse(Hibernate.isInitialized(individualRelationship.getIndividuala()));
        assertFalse(Hibernate.isInitialized(individualRelationship.getIndividualB()));
    }

    @Test
    public void selectedParentUuidsAreNotAddedToSerialisedEntities() {
        JsonNode encounter = mapper.valueToTree(encounterRepository.findByUuid(encounterUuid));
        JsonNode programEnrolment = mapper.valueToTree(programEnrolmentRepository.findByUuid(enrolment.getUuid()));
        JsonNode programEncounter = mapper.valueToTree(programEncounterRepository.findByUuid(programEncounterUuid));
        JsonNode loadedChecklist = mapper.valueToTree(checklistRepository.findByUuid(checklist.getUuid()));
        JsonNode checklistItem = mapper.valueToTree(checklistItemRepository.findByUuid(checklistItemUuid));
        JsonNode subjectMigration = mapper.valueToTree(subjectMigrationRepository.findByUuid(subjectMigrationUuid));
        JsonNode individualRelationship = mapper.valueToTree(individualRelationshipRepository.findByUuid(individualRelationshipUuid));

        assertFalse(encounter.has("linkedIndividualUuid"));
        assertFalse(programEnrolment.has("linkedIndividualUuid"));
        assertFalse(programEncounter.has("linkedProgramEnrolmentUuid"));
        assertFalse(loadedChecklist.has("linkedProgramEnrolmentUuid"));
        assertFalse(checklistItem.has("linkedChecklistUuid"));
        assertFalse(subjectMigration.has("linkedIndividualUuid"));
        assertFalse(individualRelationship.has("linkedIndividualAUuid"));
        assertFalse(individualRelationship.has("linkedIndividualBUuid"));
    }

    @Test
    public void justSavedEntityLinksParentFromTheAssociation() {
        Encounter encounter = encounterRepository.findByUuid(encounterUuid);
        Encounter newEncounter = new Encounter();
        newEncounter.assignUUID();
        newEncounter.setEncounterType(encounter.getEncounterType());
        newEncounter.setIndividual(testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subject.getSubjectType()).withLocation(subject.getAddressLevel()).build()));
        newEncounter.setAddressId(subject.getAddressLevel().getId());
        newEncounter.setObservations(new ObservationCollection());
        Encounter saved = encounterRepository.save(newEncounter);

        EntityModel<Encounter> resource = encounterController.process(EntityModel.of(saved));

        assertEquals(saved.getIndividual().getUuid(), resource.getRequiredLink("individualUUID").getHref());
    }

    @Test
    public void encounterSearchLinksSubjectWithoutLoadingIt() {
        List<Encounter> encounters = encounterSearchRepository.search(new EncounterSearchRequest(null, null, null, subject.getUuid(), Collections.emptyMap(), PageRequest.of(0, 10)));

        assertEquals(1, encounters.size());
        Encounter encounter = encounters.get(0);
        assertEquals(subject.getUuid(), encounter.getLinkedIndividualUuid());
        assertFalse(Hibernate.isInitialized(encounter.getIndividual()));
    }

    private IndividualRelation saveRelation(String name) {
        IndividualRelation relation = new IndividualRelation();
        relation.assignUUID();
        relation.setName(name);
        return individualRelationRepository.save(relation);
    }

    @Test
    public void groupSubjectsCanStillBeFoundByGroupAndByMemberUuid() {
        List<GroupSubject> byGroup = groupSubjectRepository.findByGroupSubjectUuidOrderByLastModifiedDateTimeAscIdAsc(household.getUuid(), PageRequest.of(0, 10)).getContent();
        List<GroupSubject> byMember = groupSubjectRepository.findByMemberSubjectUuidOrderByLastModifiedDateTimeAscIdAsc(subject.getUuid(), PageRequest.of(0, 10)).getContent();

        assertEquals(List.of(groupSubjectUuid), byGroup.stream().map(GroupSubject::getUuid).toList());
        assertEquals(List.of(groupSubjectUuid), byMember.stream().map(GroupSubject::getUuid).toList());
    }
}
