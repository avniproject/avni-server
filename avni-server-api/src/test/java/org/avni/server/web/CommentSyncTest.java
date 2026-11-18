package org.avni.server.web;

import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.CommentThreadRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.dao.UserSubjectRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.TestUserSyncSettingsBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestUserSubjectAssignmentBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.service.UserService;
import org.avni.server.service.builder.*;
import org.avni.server.service.sync.TestSyncService;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class CommentSyncTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private TestSyncService testSyncService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserSubjectRepository userSubjectRepository;
    @Autowired
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private CommentThreadRepository commentThreadRepository;
    @Autowired
    private EncounterRepository encounterRepository;
    @Autowired
    private ProgramEncounterRepository programEncounterRepository;
    @Autowired
    private CommentController commentController;
    @Autowired
    private CommentThreadController commentThreadController;
    @Autowired
    private EncounterController encounterController;
    @Autowired
    private ProgramEncounterController programEncounterController;

    private TestDataSetupService.TestOrganisationData organisationData;
    private TestDataSetupService.TestCatchmentData catchmentData;
    private SubjectType userSubjectType;
    private SubjectType personSubjectType;
    private User user;
    private User otherUser;
    private Individual ownUserSubject;
    private Individual otherUserSubject;

    @Before
    public void setup() {
        organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();

        setCommentsEnabled(true);

        userSubjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("userSubjectType")
                        .setName("userSubjectType")
                        .setType(Subject.User)
                        .build());
        personSubjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("personSubjectType")
                        .setName("personSubjectType")
                        .setType(Subject.Person)
                        .build());
        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), userSubjectType, personSubjectType);

        user = userRepository.save(new UserBuilder(organisationData.getUser()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());
        otherUser = userRepository.save(new UserBuilder(organisationData.getUser2()).withCatchment(catchmentData.getCatchment()).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());

        userService.ensureSubjectForUser(user, userSubjectType);
        userService.ensureSubjectForUser(otherUser, userSubjectType);
        ownUserSubject = userSubjectRepository.findByUser(user).getSubject();
        otherUserSubject = userSubjectRepository.findByUser(otherUser).getSubject();
    }

    @Test
    @Transactional
    public void commentThreadSyncForUserSubjectTypeReturnsOnlyThreadsOnOwnUserSubject() {
        CommentThread ownThread = saveThreadWithComment(ownUserSubject);
        CommentThread otherThread = saveThreadWithComment(otherUserSubject);

        setUser(user.getUsername());
        assertTrue(hasEntity(ownThread, getCommentThreads(userSubjectType)));
        assertFalse(hasEntity(otherThread, getCommentThreads(userSubjectType)));
        assertTrue(hasEntity(ownThread, getCommentThreadsAsSlice(userSubjectType)));
        assertFalse(hasEntity(otherThread, getCommentThreadsAsSlice(userSubjectType)));

        setUser(otherUser.getUsername());
        assertTrue(hasEntity(otherThread, getCommentThreads(userSubjectType)));
        assertFalse(hasEntity(ownThread, getCommentThreads(userSubjectType)));
    }

    @Test
    @Transactional
    public void commentSyncForUserSubjectTypeReturnsOnlyCommentsOnOwnUserSubject() {
        Comment ownComment = firstComment(saveThreadWithComment(ownUserSubject));
        Comment otherComment = firstComment(saveThreadWithComment(otherUserSubject));

        setUser(user.getUsername());
        assertTrue(hasEntity(ownComment, getComments(userSubjectType)));
        assertFalse(hasEntity(otherComment, getComments(userSubjectType)));
        assertTrue(hasEntity(ownComment, getCommentsAsSlice(userSubjectType)));
        assertFalse(hasEntity(otherComment, getCommentsAsSlice(userSubjectType)));

        setUser(otherUser.getUsername());
        assertTrue(hasEntity(otherComment, getComments(userSubjectType)));
        assertFalse(hasEntity(ownComment, getComments(userSubjectType)));
    }

    @Test
    @Transactional
    public void commentAndCommentThreadSyncForPersonSubjectTypeRespectsCatchment() {
        Individual inTheCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(personSubjectType).withLocation(catchmentData.getAddressLevel1()).build());
        Individual outsideCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(personSubjectType).withLocation(catchmentData.getAddressLevel2()).build());
        CommentThread threadInTheCatchment = saveThreadWithComment(inTheCatchment);
        CommentThread threadOutsideCatchment = saveThreadWithComment(outsideCatchment);
        CommentThread threadOnUserSubject = saveThreadWithComment(ownUserSubject);

        setUser(user.getUsername());
        List<CommentThread> commentThreads = getCommentThreads(personSubjectType);
        assertTrue(hasEntity(threadInTheCatchment, commentThreads));
        assertFalse(hasEntity(threadOutsideCatchment, commentThreads));
        assertFalse(hasEntity(threadOnUserSubject, commentThreads));

        List<Comment> comments = getComments(personSubjectType);
        assertTrue(hasEntity(firstComment(threadInTheCatchment), comments));
        assertFalse(hasEntity(firstComment(threadOutsideCatchment), comments));
        assertFalse(hasEntity(firstComment(threadOnUserSubject), comments));
    }

    @Test
    @Transactional
    public void commentAndCommentThreadSyncForDirectlyAssignableSubjectTypeReturnsOnlyAssignedSubjects() {
        SubjectType directlyAssignableSubjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid("directlyAssignableSubjectType").setName("directlyAssignableSubjectType").setDirectlyAssignable(true).build());
        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), directlyAssignableSubjectType);
        Individual assigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(directlyAssignableSubjectType).withLocation(catchmentData.getAddressLevel1()).build());
        Individual notAssigned = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(directlyAssignableSubjectType).withLocation(catchmentData.getAddressLevel1()).build());
        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(assigned).withUser(user).build());
        CommentThread threadOnAssigned = saveThreadWithComment(assigned);
        CommentThread threadOnNotAssigned = saveThreadWithComment(notAssigned);

        setUser(user.getUsername());
        List<CommentThread> commentThreads = getCommentThreads(directlyAssignableSubjectType);
        assertTrue(hasEntity(threadOnAssigned, commentThreads));
        assertFalse(hasEntity(threadOnNotAssigned, commentThreads));

        List<Comment> comments = getComments(directlyAssignableSubjectType);
        assertTrue(hasEntity(firstComment(threadOnAssigned), comments));
        assertFalse(hasEntity(firstComment(threadOnNotAssigned), comments));

        List syncDetails = testSyncService.getSyncDetails();
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Comment.name(), directlyAssignableSubjectType.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.CommentThread.name(), directlyAssignableSubjectType.getUuid())));
    }

    @Test
    @Transactional
    public void commentAndCommentThreadSyncForSubjectTypeWithSyncAttributeReturnsOnlySubjectsMatchingUserSyncSettings() {
        TestDataSetupService.TestSyncAttributeBasedSubjectTypeData syncAttributeData = testDataSetupService.setupSubjectTypeWithSyncAttributes();
        SubjectType subjectTypeWithSyncAttribute = syncAttributeData.getSubjectType();
        Concept syncConcept = syncAttributeData.getSyncConcept();
        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectTypeWithSyncAttribute);
        Individual matching = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttribute).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(syncConcept, syncConcept.getAnswerConcept("Answer 1").getUuid())).build());
        Individual notMatching = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectTypeWithSyncAttribute).withLocation(catchmentData.getAddressLevel1()).withObservations(ObservationCollectionBuilder.withOneObservation(syncConcept, syncConcept.getAnswerConcept("Answer 2").getUuid())).build());
        CommentThread threadOnMatching = saveThreadWithComment(matching);
        CommentThread threadOnNotMatching = saveThreadWithComment(notMatching);

        UserSyncSettings userSyncSettings = new TestUserSyncSettingsBuilder().setSubjectTypeUUID(subjectTypeWithSyncAttribute.getUuid()).setSyncConcept1(syncConcept.getUuid()).setSyncConcept1Values(Collections.singletonList(syncConcept.getAnswerConcept("Answer 1").getUuid())).build();
        userRepository.save(new UserBuilder(user).withSubjectTypeSyncSettings(userSyncSettings).build());

        setUser(user.getUsername());
        List<CommentThread> commentThreads = getCommentThreads(subjectTypeWithSyncAttribute);
        assertTrue(hasEntity(threadOnMatching, commentThreads));
        assertFalse(hasEntity(threadOnNotMatching, commentThreads));

        List<Comment> comments = getComments(subjectTypeWithSyncAttribute);
        assertTrue(hasEntity(firstComment(threadOnMatching), comments));
        assertFalse(hasEntity(firstComment(threadOnNotMatching), comments));
    }

    @Test
    @Transactional
    public void syncDetailsReportCommentAndCommentThreadOfPersonSubjectTypeOnlyWhenInsideCatchment() {
        Individual outsideCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(personSubjectType).withLocation(catchmentData.getAddressLevel2()).build());
        saveThreadWithComment(outsideCatchment);

        setUser(user.getUsername());
        List syncDetails = testSyncService.getSyncDetails();
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Comment.name(), personSubjectType.getUuid())));
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.CommentThread.name(), personSubjectType.getUuid())));

        Individual inTheCatchment = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(personSubjectType).withLocation(catchmentData.getAddressLevel1()).build());
        saveThreadWithComment(inTheCatchment);

        syncDetails = testSyncService.getSyncDetails();
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Comment.name(), personSubjectType.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.CommentThread.name(), personSubjectType.getUuid())));
    }

    @Test
    @Transactional
    public void syncDetailsReportCommentAndCommentThreadOfUserSubjectTypeOnlyForTheUserOwningTheSubject() {
        saveThreadWithComment(otherUserSubject);

        setUser(user.getUsername());
        List syncDetails = testSyncService.getSyncDetails();
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Comment.name(), userSubjectType.getUuid())));
        assertFalse(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.CommentThread.name(), userSubjectType.getUuid())));

        setUser(otherUser.getUsername());
        syncDetails = testSyncService.getSyncDetails();
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Comment.name(), userSubjectType.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.CommentThread.name(), userSubjectType.getUuid())));
    }

    @Test
    @Transactional
    public void encounterProgramEnrolmentAndProgramEncounterSyncForUserSubjectTypeReturnOnlyOwnUserSubjectData() throws Exception {
        setCommentsEnabled(false);
        Program program = testProgramService.addProgram(new ProgramBuilder().withName("userSubjectTypeProgram").build(), userSubjectType);
        testGroupService.giveViewProgramPrivilegeTo(organisationData.getGroup(), userSubjectType, program);
        FormMapping encounterFormMapping = testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("userSubjectTypeEncounter", userSubjectType);
        FormMapping programEncounterFormMapping = testEncounterTypeService.addProgramEncounterTypeAndGetFormMapping("userSubjectTypeProgramEncounter", userSubjectType, program);

        ProgramEnrolment ownProgramEnrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(ownUserSubject).build());
        ProgramEnrolment otherProgramEnrolment = testProgramEnrolmentService.save(new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(otherUserSubject).build());
        Encounter ownEncounter = saveEncounter(encounterFormMapping.getEncounterType(), ownUserSubject);
        Encounter otherEncounter = saveEncounter(encounterFormMapping.getEncounterType(), otherUserSubject);
        ProgramEncounter ownProgramEncounter = saveProgramEncounter(programEncounterFormMapping.getEncounterType(), ownProgramEnrolment);
        ProgramEncounter otherProgramEncounter = saveProgramEncounter(programEncounterFormMapping.getEncounterType(), otherProgramEnrolment);

        setUser(user.getUsername());

        List<ProgramEnrolment> programEnrolments = testSyncService.getEnrolments(program);
        assertTrue(hasEntity(ownProgramEnrolment, programEnrolments));
        assertFalse(hasEntity(otherProgramEnrolment, programEnrolments));

        List<Encounter> encounters = contentOf(encounterController.getEncountersByOperatingIndividualScope(DateTime.now().minusDays(1), DateTime.now(), encounterFormMapping.getEncounterType().getUuid(), PageRequest.of(0, 10)));
        assertTrue(hasEntity(ownEncounter, encounters));
        assertFalse(hasEntity(otherEncounter, encounters));

        List<ProgramEncounter> programEncounters = contentOf(programEncounterController.getProgramEncountersByOperatingIndividualScope(DateTime.now().minusDays(1), DateTime.now(), programEncounterFormMapping.getEncounterType().getUuid(), PageRequest.of(0, 10)));
        assertTrue(hasEntity(ownProgramEncounter, programEncounters));
        assertFalse(hasEntity(otherProgramEncounter, programEncounters));

        List syncDetails = testSyncService.getSyncDetails();
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.ProgramEnrolment.name(), program.getUuid())));
    }

    private void setCommentsEnabled(boolean isCommentEnabled) {
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisationData.getOrganisationId());
        organisationConfig.setSettings(new JsonObject().with("enableComments", isCommentEnabled));
        organisationConfigRepository.save(organisationConfig);
    }

    private CommentThread saveThreadWithComment(Individual subject) {
        CommentThread commentThread = new CommentThread();
        commentThread.setUuid(UUID.randomUUID().toString());
        commentThread.setStatus(CommentThread.CommentThreadStatus.Open);
        commentThread.setOpenDateTime(DateTime.now());

        Comment comment = new Comment();
        comment.setUuid(UUID.randomUUID().toString());
        comment.setText("comment");
        comment.setSubject(subject);
        comment.setCommentThread(commentThread);
        commentThread.setComments(new HashSet<>(Collections.singletonList(comment)));

        return commentThreadRepository.save(commentThread);
    }

    private Comment firstComment(CommentThread commentThread) {
        return commentThread.getComments().iterator().next();
    }

    private Encounter saveEncounter(EncounterType encounterType, Individual subject) {
        Encounter encounter = new Encounter();
        encounter.setUuid(UUID.randomUUID().toString());
        encounter.setEncounterType(encounterType);
        encounter.setIndividual(subject);
        encounter.setEncounterDateTime(DateTime.now(), user);
        encounter.setObservations(new ObservationCollection());
        return encounterRepository.save(encounter);
    }

    private ProgramEncounter saveProgramEncounter(EncounterType encounterType, ProgramEnrolment programEnrolment) {
        ProgramEncounter programEncounter = new ProgramEncounter();
        programEncounter.setUuid(UUID.randomUUID().toString());
        programEncounter.setEncounterType(encounterType);
        programEncounter.setProgramEnrolment(programEnrolment);
        programEncounter.setIndividual(programEnrolment.getIndividual());
        programEncounter.setEncounterDateTime(DateTime.now(), user);
        programEncounter.setObservations(new ObservationCollection());
        return programEncounterRepository.save(programEncounter);
    }

    private List<CommentThread> getCommentThreads(SubjectType subjectType) {
        return contentOf(commentThreadController.getCommentThreadsByOperatingIndividualScope(DateTime.now().minusDays(1), DateTime.now(), subjectType.getUuid(), PageRequest.of(0, 10)));
    }

    private List<CommentThread> getCommentThreadsAsSlice(SubjectType subjectType) {
        return contentOf(commentThreadController.getCommentThreadsByOperatingIndividualScopeAsSlice(DateTime.now().minusDays(1), DateTime.now(), subjectType.getUuid(), PageRequest.of(0, 10)));
    }

    private List<Comment> getComments(SubjectType subjectType) {
        return contentOf(commentController.getCommentsByOperatingIndividualScope(DateTime.now().minusDays(1), DateTime.now(), subjectType.getUuid(), PageRequest.of(0, 10)));
    }

    private List<Comment> getCommentsAsSlice(SubjectType subjectType) {
        return contentOf(commentController.getCommentsByOperatingIndividualScopeAsSlice(DateTime.now().minusDays(1), DateTime.now(), subjectType.getUuid(), PageRequest.of(0, 10)));
    }

    private <T> List<T> contentOf(CollectionModel<EntityModel<T>> collectionModel) {
        return collectionModel.getContent().stream().map(EntityModel::getContent).collect(Collectors.toList());
    }

    private boolean hasEntity(CHSEntity entity, List<? extends CHSEntity> entities) {
        return entities.stream().anyMatch(candidate -> candidate.getUuid().equals(entity.getUuid()));
    }
}
