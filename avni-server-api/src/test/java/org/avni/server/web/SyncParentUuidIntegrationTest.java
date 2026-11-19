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
import org.hibernate.Hibernate;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.hateoas.EntityModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired
    private JdbcTemplate jdbcTemplate;
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

        EntityModel<Encounter> resource = encounterController.wrap(List.of(encounter)).get(0);

        assertEquals(subject.getUuid(), resource.getRequiredLink("individualUUID").getHref());
        assertFalse(Hibernate.isInitialized(encounter.getIndividual()));
    }

    @Test
    public void programEnrolmentLinksSubjectWithoutLoadingIt() {
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByUuid(enrolment.getUuid());
        assertFalse(Hibernate.isInitialized(programEnrolment.getIndividual()));

        EntityModel<ProgramEnrolment> resource = programEnrolmentController.wrap(List.of(programEnrolment)).get(0);

        assertEquals(subject.getUuid(), resource.getRequiredLink("individualUUID").getHref());
        assertFalse(Hibernate.isInitialized(programEnrolment.getIndividual()));
    }

    @Test
    public void programEncounterLinksEnrolmentWithoutLoadingIt() {
        ProgramEncounter programEncounter = programEncounterRepository.findByUuid(programEncounterUuid);
        assertFalse(Hibernate.isInitialized(programEncounter.getProgramEnrolment()));

        EntityModel<ProgramEncounter> resource = programEncounterController.wrap(List.of(programEncounter)).get(0);

        assertEquals(enrolment.getUuid(), resource.getRequiredLink("programEnrolmentUUID").getHref());
        assertFalse(Hibernate.isInitialized(programEncounter.getProgramEnrolment()));
    }

    @Test
    public void checklistLinksEnrolmentWithoutLoadingIt() {
        Checklist loadedChecklist = checklistRepository.findByUuid(checklist.getUuid());
        assertFalse(Hibernate.isInitialized(loadedChecklist.getProgramEnrolment()));

        EntityModel<Checklist> resource = checklistController.wrap(List.of(loadedChecklist)).get(0);

        assertEquals(enrolment.getUuid(), resource.getRequiredLink("programEnrolmentUUID").getHref());
        assertFalse(Hibernate.isInitialized(loadedChecklist.getProgramEnrolment()));
    }

    @Test
    public void checklistItemLinksChecklistWithoutLoadingIt() {
        ChecklistItem checklistItem = checklistItemRepository.findByUuid(checklistItemUuid);
        assertFalse(Hibernate.isInitialized(checklistItem.getChecklist()));

        EntityModel<ChecklistItem> resource = checklistItemController.wrap(List.of(checklistItem)).get(0);

        assertEquals(checklist.getUuid(), resource.getRequiredLink("checklistUUID").getHref());
        assertFalse(Hibernate.isInitialized(checklistItem.getChecklist()));
    }

    @Test
    public void subjectMigrationLinksSubjectWithoutLoadingIt() {
        SubjectMigration subjectMigration = subjectMigrationRepository.findByUuid(subjectMigrationUuid);
        assertFalse(Hibernate.isInitialized(subjectMigration.getIndividual()));

        EntityModel<SubjectMigration> resource = subjectMigrationController.wrap(List.of(subjectMigration)).get(0);

        assertEquals(subject.getUuid(), resource.getRequiredLink("individualUUID").getHref());
        assertFalse(Hibernate.isInitialized(subjectMigration.getIndividual()));
    }

    @Test
    public void groupSubjectLinksAndSerialisesGroupAndMemberWithoutLoadingThem() {
        GroupSubject groupSubject = groupSubjectRepository.findByUuid(groupSubjectUuid);
        assertFalse(Hibernate.isInitialized(groupSubject.getGroupSubject()));
        assertFalse(Hibernate.isInitialized(groupSubject.getMemberSubject()));

        EntityModel<GroupSubject> resource = groupSubjectController.wrap(List.of(groupSubject)).get(0);
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

        EntityModel<IndividualRelationship> resource = individualRelationshipController.wrap(List.of(individualRelationship)).get(0);

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

        EntityModel<Encounter> resource = encounterController.wrap(List.of(saved)).get(0);

        assertEquals(saved.getIndividual().getUuid(), resource.getRequiredLink("individualUUID").getHref());
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

    @Test
    public void parentUuidLookupsForASyncPageDoNotGrowWithItsOffset() {
        ProgramEncounter programEncounter = programEncounterRepository.findByUuid(programEncounterUuid);
        for (int i = 0; i < 29; i++) {
            ProgramEncounter another = new ProgramEncounter();
            another.assignUUID();
            another.setEncounterType(programEncounter.getEncounterType());
            another.setProgramEnrolment(programEncounter.getProgramEnrolment());
            another.setIndividual(programEncounter.getIndividual());
            another.setAddressId(programEncounter.getAddressId());
            another.setEncounterDateTime(DateTime.now(), null);
            another.setObservations(new ObservationCollection());
            programEncounterRepository.save(another);
        }
        entityManager.flush();
        entityManager.clear();

        long firstPageLookups = programEnrolmentScansToSync(PageRequest.of(0, 10));
        long thirdPageLookups = programEnrolmentScansToSync(PageRequest.of(2, 10));

        assertEquals(firstPageLookups, thirdPageLookups);
    }

    private long programEnrolmentScansToSync(PageRequest pageRequest) {
        Specification<ProgramEncounter> syncOrder = (root, query, builder) -> {
            query.orderBy(builder.asc(root.get("lastModifiedDateTime")), builder.asc(root.get("id")));
            return builder.conjunction();
        };
        long scansBefore = programEnrolmentScans();
        Slice<ProgramEncounter> page = programEncounterRepository.findAllAsSlice(syncOrder, pageRequest);
        programEncounterController.wrap(page);
        long scans = programEnrolmentScans() - scansBefore;
        entityManager.clear();
        return scans;
    }

    private long programEnrolmentScans() {
        return jdbcTemplate.queryForObject("select coalesce(seq_scan, 0) + coalesce(idx_scan, 0) from pg_stat_xact_user_tables where relname = 'program_enrolment'", Long.class);
    }
}
