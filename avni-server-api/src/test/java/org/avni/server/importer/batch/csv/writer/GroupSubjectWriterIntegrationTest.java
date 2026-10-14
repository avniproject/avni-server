package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.Subject;
import org.avni.server.application.SubjectTypeSettingKey;
import org.avni.server.dao.*;
import org.avni.server.dao.individualRelationship.IndividualRelationGenderMappingRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.individualRelationship.IndividualRelation;
import org.avni.server.domain.individualRelationship.IndividualRelationGenderMapping;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.avni.server.domain.individualRelationship.IndividualRelationshipType;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.GroupSubjectService;
import org.avni.server.service.UserSubjectAssignmentService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class GroupSubjectWriterIntegrationTest extends BaseCSVImportTest {
    private static final String GROUP_ID = "Phulwari Id";
    private static final String HOUSEHOLD_ID = "Household Id";
    private static final String HEAD_OF_HOUSEHOLD = "Is head of household (yes|no)";
    private static final String RELATION = "Relation name with Head of Household";
    private static final String REMOVAL_REASON = "Removal Reason";

    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private GroupSubjectRepository groupSubjectRepository;
    @Autowired
    private GroupSubjectService groupSubjectService;
    @Autowired
    private GroupSubjectWriter groupSubjectWriter;
    @Autowired
    private GenderRepository genderRepository;
    @Autowired
    private IndividualRelationRepository individualRelationRepository;
    @Autowired
    private IndividualRelationGenderMappingRepository individualRelationGenderMappingRepository;
    @Autowired
    private IndividualRelationshipTypeRepository individualRelationshipTypeRepository;
    @Autowired
    private IndividualRelationshipRepository individualRelationshipRepository;
    @Autowired
    private UserSubjectAssignmentService userSubjectAssignmentService;
    @Autowired
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private ConceptRepository conceptRepository;

    private TransactionTemplate transactionTemplate;
    private TestDataSetupService.TestCatchmentData catchment;
    private User user;
    private Gender male;
    private SubjectType memberType;
    private GroupRole childRole;
    private Individual phulwari;
    private Individual ravi;
    private Individual household;
    private Individual father;
    private Individual grandfather;
    private Individual son;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        transactionTemplate = new TransactionTemplate(platformTransactionManager);
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        catchment = testDataSetupService.setupACatchment();
        user = userRepository.save(new UserBuilder().withCatchment(catchment.getCatchment()).withDefaultValuesForNewEntity().userName("assignee@example").withAuditUser(organisationData.getUser()).organisationId(organisationData.getOrganisationId()).build());

        memberType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Child").setUuid("st_Child").build());
        SubjectType phulwariType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Phulwari").setUuid("st_Phulwari").setType(Subject.Group).setGroup(true).setDirectlyAssignable(true).build());
        SubjectType householdType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Household").setUuid("st_Household").setType(Subject.Household).setGroup(true).setHousehold(true).build());
        childRole = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withRole("Child").withGroupSubjectType(phulwariType).withMemberSubjectType(memberType).build());
        groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withRole("Monitor").withGroupSubjectType(phulwariType).withMemberSubjectType(memberType).build());
        groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withRole("Head of household").withGroupSubjectType(householdType).withMemberSubjectType(memberType).withMaxNumberOfMembers(1).build());
        groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withRole("Member").withGroupSubjectType(householdType).withMemberSubjectType(memberType).build());

        male = new Gender();
        male.setName("Male");
        male.assignUUID();
        male = genderRepository.save(male);
        IndividualRelation fatherRelation = relation("Father");
        IndividualRelation sonRelation = relation("Son");
        IndividualRelationshipType fatherSon = new IndividualRelationshipType();
        fatherSon.setName("Father-Son");
        fatherSon.setIndividualAIsToB(fatherRelation);
        fatherSon.setIndividualBIsToA(sonRelation);
        fatherSon.assignUUID();
        individualRelationshipTypeRepository.save(fatherSon);

        phulwari = subject(phulwariType, "PHUL-1");
        ravi = subject(memberType, "RAVI");
        household = subject(householdType, "HH-1");
        father = subject(memberType, "FATHER");
        grandfather = subject(memberType, "GRANDFATHER");
        son = subject(memberType, "SON");
    }

    @Test
    public void sample1054_twoFinishedPeriods_loadAsTwoEndedMemberships_inEitherOrder() {
        Individual asha = subject(memberType, "ASHA");

        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-12-21"));
        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24"));
        succeeds(groupRow("ASHA", "Child", "2025-03-01", "2025-05-24"));
        succeeds(groupRow("ASHA", "Child", "2025-08-01", "2025-12-21"));

        List<String> expected = List.of("2025-03-01 2025-05-24 ended Child", "2025-08-01 2025-12-21 ended Child");
        assertEquals(expected, memberships(phulwari, ravi));
        assertEquals(expected, memberships(phulwari, asha));
    }

    @Test
    public void sample1044_activeAndEndedPeriods_loadSideBySide_inEitherOrder() {
        Individual asha = subject(memberType, "ASHA");

        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24"));
        succeeds(groupRow("ASHA", "Child", "2025-03-01", "2025-05-24"));
        succeeds(groupRow("ASHA", "Child", "2025-08-01", ""));

        List<String> expected = List.of("2025-03-01 2025-05-24 ended Child", "2025-08-01 open active Child");
        assertEquals(expected, memberships(phulwari, ravi));
        assertEquals(expected, memberships(phulwari, asha));
    }

    @Test
    public void rowWithEndDateButNoStartDate_isRejected() {
        fails(groupRow("RAVI", "Child", "", "2025-05-24"), "Membership Start Date is required when Membership End Date is given");
        assertEquals(List.of(), memberships(phulwari, ravi));
    }

    @Test
    public void endDateBeforeStartDate_isRejected_namingBothDates() {
        String error = failure(groupRow("RAVI", "Child", "2025-06-01", "2025-05-24"));
        assertTrue(error, error.contains("2025-05-24") && error.contains("2025-06-01"));
        assertEquals(List.of(), memberships(phulwari, ravi));
    }

    @Test
    public void endDateAfterToday_isRejected_namingTheDate() {
        String tomorrow = LocalDate.now(DateTimeUtil.IST).plusDays(1).toString();
        String error = failure(groupRow("RAVI", "Child", "2025-06-01", tomorrow));
        assertTrue(error, error.contains(tomorrow));
        assertEquals(List.of(), memberships(phulwari, ravi));
    }

    @Test
    public void endDateToday_isAccepted() {
        String today = LocalDate.now(DateTimeUtil.IST).toString();
        succeeds(groupRow("RAVI", "Child", "2025-06-01", today));
        assertEquals(List.of("2025-06-01 " + today + " ended Child"), memberships(phulwari, ravi));
    }

    @Test
    public void blankStartDate_withNoEndDate_startsToday_andIsRejectedWhenAlreadyActive() {
        succeeds(groupRow("RAVI", "Child", "", ""));
        assertEquals(List.of(LocalDate.now(DateTimeUtil.IST) + " open active Child"), memberships(phulwari, ravi));

        fails(groupRow("RAVI", "Child", "", ""), "Member is already an active member of this group");
        assertEquals(1, memberships(phulwari, ravi).size());
    }

    @Test
    public void memberWithMoreThanOneActiveMembership_isRejected_whateverTheRowsDates() {
        addMembershipInApp(ravi, new DateTime(2024, 12, 27, 10, 0, DateTimeUtil.IST));
        addMembershipInApp(ravi, new DateTime(2024, 12, 27, 10, 0, DateTimeUtil.IST));
        String error = "Member has more than one active membership in this group. Remove the extra ones in the app, then upload again.";

        fails(groupRow("RAVI", "Child", "2024-12-27", ""), error);
        fails(groupRow("RAVI", "Child", "2024-12-27", "2025-01-10"), error);
        fails(groupRow("RAVI", "Child", "2023-01-01", "2023-02-01"), error);
        assertEquals(List.of("2024-12-27 open active Child", "2024-12-27 open active Child"), memberships(phulwari, ravi));
    }

    @Test
    public void matchedRowWithADifferentRole_isRejected() {
        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        succeeds(groupRow("RAVI", "Child", "2025-01-01", "2025-02-01"));
        String error = "Role differs from the existing membership. Change it in the app.";

        fails(groupRow("RAVI", "Monitor", "2025-08-01", ""), error);
        fails(groupRow("RAVI", "Monitor", "2025-08-01", "2025-09-01"), error);
        fails(groupRow("RAVI", "Monitor", "2025-01-01", "2025-02-01"), error);
        assertEquals(List.of("2025-01-01 2025-02-01 ended Child", "2025-08-01 open active Child"), memberships(phulwari, ravi));
    }

    @Test
    public void exactMatchOfAnActiveMembershipAddedInTheApp_changesNothing() {
        DateTime startedInApp = new DateTime(2025, 8, 1, 1, 30, DateTimeUtil.IST);
        GroupSubject added = addMembershipInApp(ravi, startedInApp);
        GroupSubject before = reload(added);

        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));

        GroupSubject after = reload(added);
        assertEquals(before.getVersion(), after.getVersion());
        assertEquals(before.getLastModifiedDateTime(), after.getLastModifiedDateTime());
        assertEquals(startedInApp.getMillis(), after.getMembershipStartDate().getMillis());
        assertEquals(1, memberships(phulwari, ravi).size());
    }

    @Test
    public void endingAnActiveMembershipAddedInTheApp_endsIt_withoutCreatingAnother() {
        DateTime startedInApp = new DateTime(2025, 8, 1, 1, 30, DateTimeUtil.IST);
        GroupSubject added = addMembershipInApp(ravi, startedInApp);

        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-09-15"));

        assertEquals(List.of("2025-08-01 2025-09-15 ended Child"), memberships(phulwari, ravi));
        GroupSubject ended = reload(added);
        assertTrue(ended.isVoided());
        assertEquals(startedInApp.getMillis(), ended.getMembershipStartDate().getMillis());
    }

    @Test
    public void endingRowWithAStartDateADayOff_isRejected_andTheMemberStaysActive() {
        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));

        fails(groupRow("RAVI", "Child", "2025-08-02", "2025-09-15"), "Member is active in this group from 2025-08-01. Give that start date to end it.");
        assertEquals(List.of("2025-08-01 open active Child"), memberships(phulwari, ravi));
    }

    @Test
    public void endedMembership_isNeverChanged_orReopened() {
        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24"));

        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24"));
        fails(groupRow("RAVI", "Child", "2025-03-01", "2025-05-30"), "Overlaps an ended membership from 2025-03-01 to 2025-05-24");
        fails(groupRow("RAVI", "Child", "2025-03-01", ""), "Overlaps an ended membership from 2025-03-01 to 2025-05-24");
        fails(groupRow("RAVI", "Child", "2025-04-01", ""), "Overlaps an ended membership from 2025-03-01 to 2025-05-24");
        assertEquals(List.of("2025-03-01 2025-05-24 ended Child"), memberships(phulwari, ravi));
    }

    @Test
    public void endedPeriodOverlappingAnActiveOne_isRejected_inEitherOrder() {
        Individual asha = subject(memberType, "ASHA");

        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        fails(groupRow("RAVI", "Child", "2025-07-01", "2025-09-01"), "Member is active in this group from 2025-08-01. Give that start date to end it.");
        succeeds(groupRow("ASHA", "Child", "2025-07-01", "2025-09-01"));
        fails(groupRow("ASHA", "Child", "2025-08-01", ""), "Overlaps an ended membership from 2025-07-01 to 2025-09-01");

        assertEquals(List.of("2025-08-01 open active Child"), memberships(phulwari, ravi));
        assertEquals(List.of("2025-07-01 2025-09-01 ended Child"), memberships(phulwari, asha));
    }

    @Test
    public void sameDayRejoin_loadsSideBySide_inEitherOrder() {
        Individual asha = subject(memberType, "ASHA");

        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-08-01"));
        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        succeeds(groupRow("ASHA", "Child", "2025-08-01", ""));
        succeeds(groupRow("ASHA", "Child", "2025-08-01", "2025-08-01"));

        List<String> expected = List.of("2025-08-01 2025-08-01 ended Child", "2025-08-01 open active Child");
        assertEquals(expected, memberships(phulwari, ravi));
        assertEquals(expected, memberships(phulwari, asha));
    }

    @Test
    public void periodEndingOnTheDayAnotherStarts_loadsSideBySide_inEitherOrder() {
        Individual asha = subject(memberType, "ASHA");

        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-08-01"));
        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        succeeds(groupRow("ASHA", "Child", "2025-08-01", ""));
        succeeds(groupRow("ASHA", "Child", "2025-03-01", "2025-08-01"));

        List<String> expected = List.of("2025-03-01 2025-08-01 ended Child", "2025-08-01 open active Child");
        assertEquals(expected, memberships(phulwari, ravi));
        assertEquals(expected, memberships(phulwari, asha));
    }

    @Test
    public void twoEndedPeriodsOnTheSameDay_loadAsOne() {
        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-08-01"));
        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-08-01"));
        assertEquals(List.of("2025-08-01 2025-08-01 ended Child"), memberships(phulwari, ravi));
    }

    @Test
    public void twoEndedPeriodsStartingOnTheSameDay_oneEndingThatDay_loadInEitherOrder_withSameOrDifferentRoles() {
        Individual asha = subject(memberType, "ASHA");
        Individual meena = subject(memberType, "MEENA");
        Individual geeta = subject(memberType, "GEETA");

        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-08-01"));
        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-08-10"));
        succeeds(groupRow("ASHA", "Child", "2025-08-01", "2025-08-10"));
        succeeds(groupRow("ASHA", "Child", "2025-08-01", "2025-08-01"));
        succeeds(groupRow("MEENA", "Child", "2025-08-01", "2025-08-01"));
        succeeds(groupRow("MEENA", "Monitor", "2025-08-01", "2025-08-10"));
        succeeds(groupRow("GEETA", "Monitor", "2025-08-01", "2025-08-10"));
        succeeds(groupRow("GEETA", "Child", "2025-08-01", "2025-08-01"));

        List<String> sameRole = List.of("2025-08-01 2025-08-01 ended Child", "2025-08-01 2025-08-10 ended Child");
        List<String> differentRoles = List.of("2025-08-01 2025-08-01 ended Child", "2025-08-01 2025-08-10 ended Monitor");
        assertEquals(sameRole, memberships(phulwari, ravi));
        assertEquals(sameRole, memberships(phulwari, asha));
        assertEquals(differentRoles, memberships(phulwari, meena));
        assertEquals(differentRoles, memberships(phulwari, geeta));
    }

    @Test
    public void endingRowOnTheActiveMembershipsStartDay_createsASeparateEndedMembership() {
        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        succeeds(groupRow("RAVI", "Child", "2025-08-01", "2025-08-01"));
        assertEquals(List.of("2025-08-01 2025-08-01 ended Child", "2025-08-01 open active Child"), memberships(phulwari, ravi));
    }

    @Test
    public void uploadingTheSameFileTwice_writesNothing() {
        assignToUser(phulwari);
        List<String[]> file = List.of(
                groupRow("RAVI", "Child", "2025-08-01", ""),
                groupRow("RAVI", "Child", "2025-03-01", "2025-05-24"));
        file.forEach(this::succeeds);
        List<String> afterFirstUpload = memberships(phulwari, ravi);
        List<String> versionsAfterFirstUpload = versions(phulwari, ravi);
        unassignByHand(ravi);

        file.forEach(this::succeeds);
        fails(groupRow("RAVI", "Child", "", ""), "Member is already an active member of this group");

        assertEquals(afterFirstUpload, memberships(phulwari, ravi));
        assertEquals(versionsAfterFirstUpload, versions(phulwari, ravi));
        assertNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user, ravi));
    }

    @Test
    public void sameFileExportedADayOff_isRejected_ratherThanDuplicated() {
        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24"));

        fails(groupRow("RAVI", "Child", "2025-07-31", ""), "Member is already an active member of this group");
        fails(groupRow("RAVI", "Child", "2025-02-28", "2025-05-23"), "Overlaps an ended membership from 2025-03-01 to 2025-05-24");
        assertEquals(List.of("2025-03-01 2025-05-24 ended Child", "2025-08-01 open active Child"), memberships(phulwari, ravi));
    }

    @Test
    public void membershipDeletedOnTheWebApp_canBeUploadedAgain() {
        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        inTransaction(() -> {
            GroupSubject groupSubject = groupSubjectRepository.findAllByMemberSubject(ravi).get(0);
            groupSubject.setVoided(true);
            return groupSubjectRepository.save(groupSubject);
        });

        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        assertEquals(List.of("2025-08-01 open active Child", "2025-08-01 open deleted Child"), memberships(phulwari, ravi));
    }

    @Test
    public void household_withAPastHeadAndACurrentHead_loads() {
        succeeds(householdRow("GRANDFATHER", "yes", "", "2020-01-01", "2024-12-31"));
        succeeds(householdRow("FATHER", "yes", "", "2025-01-01", ""));
        succeeds(householdRow("SON", "no", "Son", "2025-01-01", ""));
        succeeds(householdRow("SON", "no", "", "2020-01-01", "2024-12-31"));

        assertEquals(List.of("2020-01-01 2024-12-31 ended Head of household"), memberships(household, grandfather));
        assertEquals(List.of("2025-01-01 open active Head of household"), memberships(household, father));
        assertEquals(List.of("2020-01-01 2024-12-31 ended Member", "2025-01-01 open active Member"), memberships(household, son));
        assertEquals(List.of(), relationshipsOf(grandfather));
        assertEquals(List.of("FATHER Father-Son SON"), relationshipsOf(son));
    }

    @Test
    public void household_whereEveryMemberHasLeft_loads() {
        succeeds(householdRow("SON", "no", "", "2020-01-01", "2024-12-31"));
        succeeds(householdRow("FATHER", "yes", "", "2020-01-01", "2024-12-31"));

        assertEquals(List.of("2020-01-01 2024-12-31 ended Head of household"), memberships(household, father));
        assertEquals(List.of("2020-01-01 2024-12-31 ended Member"), memberships(household, son));
        assertEquals(List.of(), relationshipsOf(son));
    }

    @Test
    public void household_activeMemberRowBeforeItsHeadsRow_fails() {
        fails(householdRow("SON", "no", "Son", "2025-01-01", ""), "Head of household not yet defined for Household id 'HH-1'. Cannot add member.");
        succeeds(householdRow("FATHER", "yes", "", "2025-01-01", ""));
        assertEquals(List.of(), memberships(household, son));
    }

    @Test
    public void household_reUpload_leavesRelationshipsWithTheHeadAlone() {
        List<String[]> file = List.of(
                householdRow("FATHER", "yes", "", "2025-01-01", ""),
                householdRow("SON", "no", "Son", "2025-01-01", ""));
        file.forEach(this::succeeds);
        List<String> relationshipIdsAfterFirstUpload = relationshipIdsOf(son);

        file.forEach(this::succeeds);

        assertEquals(relationshipIdsAfterFirstUpload, relationshipIdsOf(son));
        assertEquals(List.of("FATHER Father-Son SON"), relationshipsOf(son));
    }

    @Test
    public void household_headFlagDifferingFromTheExistingMembership_isRejected() {
        succeeds(householdRow("FATHER", "yes", "", "2025-01-01", ""));
        fails(householdRow("FATHER", "no", "Son", "2025-01-01", ""), "Role differs from the existing membership. Change it in the app.");
        assertEquals(List.of("2025-01-01 open active Head of household"), memberships(household, father));
    }

    @Test
    public void endedMembership_ofAGroupAssignedDirectlyToUsers_doesNotAssignTheMember() {
        assignToUser(phulwari);

        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24"));
        assertNull(userSubjectAssignmentRepository.findByUserAndSubject(user, ravi));

        succeeds(groupRow("RAVI", "Child", "2025-08-01", ""));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user, ravi));
    }

    @Test
    public void endingAMembershipInTheApp_leavesAMemberUnassignedByHandUnassigned() {
        assignToUser(phulwari);
        GroupSubject added = addMembershipInApp(ravi, new DateTime(2025, 8, 1, 10, 0, DateTimeUtil.IST));
        assertNotNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user, ravi));
        unassignByHand(ravi);

        inTransaction(() -> {
            GroupSubject groupSubject = groupSubjectRepository.findOne(added.getId());
            groupSubject.setMembershipEndDate(new DateTime(2025, 9, 1, 10, 0, DateTimeUtil.IST));
            groupSubject.setVoided(true);
            return groupSubjectService.save(groupSubject);
        });

        assertNull(userSubjectAssignmentRepository.findByUserAndSubjectAndIsVoidedFalse(user, ravi));
    }

    private String[] groupRow(String memberId, String role, String startDate, String endDate) {
        return new String[]{"PHUL-1", memberId, role, startDate, endDate};
    }

    private String[] householdRow(String memberId, String isHead, String relation, String startDate, String endDate) {
        return new String[]{"HH-1", memberId, isHead, relation, startDate, endDate};
    }

    private Row row(String[] cells) {
        List<String> headers = new ArrayList<>(cells[0].startsWith("HH")
                ? List.of(HOUSEHOLD_ID, "Member Id", HEAD_OF_HOUSEHOLD, RELATION, "Membership Start Date", "Membership End Date")
                : List.of(GROUP_ID, "Member Id", "Role", "Membership Start Date", "Membership End Date"));
        if (cells.length > headers.size()) {
            headers.add(REMOVAL_REASON);
        }
        return new Row(header(headers.toArray(new String[0])), dataRow(cells));
    }

    private void succeeds(String[] cells) {
        String error = failure(cells);
        assertNull(error, error);
    }

    private void fails(String[] cells, String expectedError) {
        assertEquals(expectedError, failure(cells));
    }

    private String failure(String[] cells) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    groupSubjectWriter.write(new Chunk<>(row(cells)));
                } catch (Exception e) {
                    throw new RowFailedException(e);
                }
            });
            return null;
        } catch (RowFailedException e) {
            return e.getCause().getMessage();
        }
    }

    private List<String> memberships(Individual group, Individual member) {
        return inTransaction(() -> groupSubjectRepository.findAllByMemberSubject(member).stream()
                .filter(groupSubject -> groupSubject.getGroupSubject().getId().equals(group.getId()))
                .map(groupSubject -> String.format("%s %s %s %s",
                        day(groupSubject.getMembershipStartDate()),
                        groupSubject.getMembershipEndDate() == null ? "open" : day(groupSubject.getMembershipEndDate()),
                        !groupSubject.isVoided() ? "active" : groupSubject.getMembershipEndDate() == null ? "deleted" : "ended",
                        groupSubject.getGroupRole().getRole()))
                .sorted()
                .toList());
    }

    private List<String> versions(Individual group, Individual member) {
        return inTransaction(() -> groupSubjectRepository.findAllByMemberSubject(member).stream()
                .filter(groupSubject -> groupSubject.getGroupSubject().getId().equals(group.getId()))
                .map(groupSubject -> groupSubject.getId() + ":" + groupSubject.getVersion() + ":" + groupSubject.getLastModifiedDateTime())
                .sorted()
                .toList());
    }

    private List<String> relationshipsOf(Individual member) {
        return inTransaction(() -> individualRelationshipRepository.findByIndividual(member).stream()
                .filter(relationship -> !relationship.isVoided())
                .map(relationship -> String.format("%s %s %s", relationship.getIndividuala().getLegacyId(), relationship.getRelationship().getName(), relationship.getIndividualB().getLegacyId()))
                .sorted()
                .toList());
    }

    private List<String> relationshipIdsOf(Individual member) {
        return inTransaction(() -> individualRelationshipRepository.findByIndividual(member).stream()
                .map(relationship -> relationship.getId() + ":" + relationship.isVoided() + ":" + relationship.getVersion())
                .sorted()
                .toList());
    }

    private String day(DateTime dateTime) {
        return dateTime.withZone(DateTimeUtil.IST).toLocalDate().toString();
    }

    private GroupSubject addMembershipInApp(Individual member, DateTime membershipStartDate) {
        return inTransaction(() -> {
            GroupSubject groupSubject = new TestGroupSubjectBuilder().withGroup(phulwari).withMember(member).withGroupRole(childRole).build();
            groupSubject.setMembershipStartDate(membershipStartDate);
            return groupSubjectService.save(groupSubject);
        });
    }

    private GroupSubject reload(GroupSubject groupSubject) {
        return inTransaction(() -> groupSubjectRepository.findOne(groupSubject.getId()));
    }

    private void assignToUser(Individual group) {
        inTransaction(() -> {
            userSubjectAssignmentService.assignSubjects(user, Collections.singletonList(individualRepository.findOne(group.getId())), false);
            return null;
        });
    }

    private void unassignByHand(Individual member) {
        inTransaction(() -> {
            UserSubjectAssignment assignment = userSubjectAssignmentRepository.findByUserAndSubject(user, member);
            assignment.setVoided(true);
            return userSubjectAssignmentRepository.save(assignment);
        });
    }

    private Individual subject(SubjectType subjectType, String legacyId) {
        Individual subject = new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchment.getAddressLevel1()).withLegacyId(legacyId).build();
        subject.setGender(male);
        return individualRepository.save(subject);
    }

    private IndividualRelation relation(String name) {
        IndividualRelation relation = new IndividualRelation();
        relation.setName(name);
        relation.assignUUID();
        relation = individualRelationRepository.save(relation);
        IndividualRelationGenderMapping genderMapping = new IndividualRelationGenderMapping(relation, male);
        genderMapping.assignUUID();
        individualRelationGenderMappingRepository.save(genderMapping);
        return relation;
    }

    private <T> T inTransaction(Work<T> work) {
        return transactionTemplate.execute(status -> {
            try {
                return work.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private interface Work<T> {
        T get() throws Exception;
    }

    private static class RowFailedException extends RuntimeException {
        RowFailedException(Exception cause) {
            super(cause);
        }
    }

    @Test
    public void removalReason_isRequiredOnAnEndingRow_whereTheGroupTypeAsksForOne() {
        Individual anganwadi = groupWithRemovalReasons();

        fails(reasonRow("ANGAN-1", "RAVI", "2025-03-01", "2025-05-24", ""), "Removal Reason is required for this group type");
        fails(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24", "ANGAN-1"), "Removal Reason is required for this group type");
        assertEquals(List.of(), memberships(anganwadi, ravi));
    }

    @Test
    public void removalReason_givenByName_isSavedOnTheEndedMembership() {
        Individual anganwadi = groupWithRemovalReasons();
        Individual asha = subject(memberType, "ASHA");

        succeeds(reasonRow("ANGAN-1", "RAVI", "2025-03-01", "2025-05-24", "moved AWAY"));
        succeeds(reasonRow("ANGAN-1", "ASHA", "2025-08-01", ""));
        succeeds(reasonRow("ANGAN-1", "ASHA", "2025-08-01", "2025-09-01", "Lost contact"));

        assertEquals(List.of("2025-03-01 2025-05-24 Moved away"), removalReasons(anganwadi, ravi));
        assertEquals(List.of("2025-08-01 2025-09-01 Lost contact"), removalReasons(anganwadi, asha));
    }

    @Test
    public void removalReason_notOfferedByTheGroupType_isRejected_listingTheAllowedAnswers() {
        Individual anganwadi = groupWithRemovalReasons();

        fails(reasonRow("ANGAN-1", "RAVI", "2025-03-01", "2025-05-24", "Married"), "Removal Reason 'Married' is not one of: Lost contact, Moved away");
        assertEquals(List.of(), memberships(anganwadi, ravi));
    }

    @Test
    public void removalReason_onARowWithNoEndDate_isRejected() {
        Individual anganwadi = groupWithRemovalReasons();
        String error = "Removal Reason is given only with Membership End Date";

        fails(reasonRow("ANGAN-1", "RAVI", "2025-08-01", "", "Moved away"), error);
        fails(reasonRow("PHUL-1", "RAVI", "2025-08-01", "", "Moved away"), error);
        assertEquals(List.of(), memberships(anganwadi, ravi));
        assertEquals(List.of(), memberships(phulwari, ravi));
    }

    @Test
    public void removalReason_whereTheGroupTypeDoesNotAskForOne_mayBeBlank_andIsRejectedWhenGiven() {
        fails(reasonRow("PHUL-1", "RAVI", "2025-03-01", "2025-05-24", "Moved away"), "Removal Reason is not asked for this group type");
        succeeds(reasonRow("PHUL-1", "RAVI", "2025-03-01", "2025-05-24", ""));
        fails(new String[]{"HH-1", "SON", "no", "", "2020-01-01", "2024-12-31", "Moved away"}, "Removal Reason is not asked for this group type");
        succeeds(new String[]{"HH-1", "SON", "no", "", "2020-01-01", "2024-12-31", ""});

        assertEquals(List.of("2025-03-01 2025-05-24 none"), removalReasons(phulwari, ravi));
        assertEquals(List.of("2020-01-01 2024-12-31 none"), removalReasons(household, son));
    }

    @Test
    public void removalReason_isIgnoredOnAnExactMatch() {
        Individual anganwadi = groupWithRemovalReasons();
        succeeds(reasonRow("ANGAN-1", "RAVI", "2025-03-01", "2025-05-24", "Moved away"));
        List<String> versionsAfterFirstUpload = versions(anganwadi, ravi);

        succeeds(reasonRow("ANGAN-1", "RAVI", "2025-03-01", "2025-05-24", "Lost contact"));
        succeeds(reasonRow("ANGAN-1", "RAVI", "2025-03-01", "2025-05-24", ""));
        succeeds(groupRow("RAVI", "Child", "2025-03-01", "2025-05-24", "ANGAN-1"));

        assertEquals(versionsAfterFirstUpload, versions(anganwadi, ravi));
        assertEquals(List.of("2025-03-01 2025-05-24 Moved away"), removalReasons(anganwadi, ravi));
    }

    private String[] reasonRow(String groupId, String memberId, String startDate, String endDate, String... removalReason) {
        List<String> cells = new ArrayList<>(List.of(groupId, memberId, "Child", startDate, endDate));
        cells.addAll(List.of(removalReason.length == 0 ? new String[]{""} : removalReason));
        return cells.toArray(new String[0]);
    }

    private String[] groupRow(String memberId, String role, String startDate, String endDate, String groupId) {
        return new String[]{groupId, memberId, role, startDate, endDate};
    }

    private Individual groupWithRemovalReasons() {
        Concept removalReason = testConceptService.createCodedConcept("Anganwadi Removal Reason", "Moved away", "Lost contact");
        SubjectType anganwadiType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Anganwadi").setUuid("st_Anganwadi").setType(Subject.Group).setGroup(true).build());
        JsonObject settings = anganwadiType.getSettings() == null ? new JsonObject() : anganwadiType.getSettings();
        settings.put(String.valueOf(SubjectTypeSettingKey.removalReasonConceptUuid), removalReason.getUuid());
        anganwadiType.setSettings(settings);
        anganwadiType = subjectTypeRepository.save(anganwadiType);
        groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withRole("Child").withGroupSubjectType(anganwadiType).withMemberSubjectType(memberType).build());
        return subject(anganwadiType, "ANGAN-1");
    }

    private List<String> removalReasons(Individual group, Individual member) {
        return inTransaction(() -> groupSubjectRepository.findAllByMemberSubject(member).stream()
                .filter(groupSubject -> groupSubject.getGroupSubject().getId().equals(group.getId()))
                .map(groupSubject -> String.format("%s %s %s",
                        day(groupSubject.getMembershipStartDate()),
                        groupSubject.getMembershipEndDate() == null ? "open" : day(groupSubject.getMembershipEndDate()),
                        groupSubject.getRemovalReasonConceptUUID() == null ? "none" : conceptRepository.findByUuid(groupSubject.getRemovalReasonConceptUUID()).getName()))
                .sorted()
                .toList());
    }
}
