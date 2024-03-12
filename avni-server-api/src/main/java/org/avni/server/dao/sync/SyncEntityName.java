package org.avni.server.dao.sync;

import org.apache.commons.collections.ListUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum SyncEntityName {
    ResetSync,
    AddressLevel,
    Subject,
    ProgramEnrolment,
    Individual,
    Enrolment,
    Encounter,
    ProgramEncounter,
    ChecklistItem,
    Checklist,
    Comment,
    CommentThread,
    IndividualRelationship,
    LocationMapping,
    Location,
    SubjectMigration,
    GroupSubject,
    SubjectProgramEligibility,
    SubjectEntityApprovalStatus,
    EncounterEntityApprovalStatus,
    ProgramEncounterEntityApprovalStatus,
    ProgramEnrolmentEntityApprovalStatus,
    ChecklistDetail,
    IdentifierAssignment,
    RuleDependency,
    Form,
    FormMapping,
    EncounterType,
    Rule, IndividualRelation, IndividualRelationGenderMapping, IndividualRelationshipType, Program, Gender, Concept, ProgramConfig, Video, SubjectType, ChecklistItemDetail, FormElementGroup, FormElement, ConceptAnswer, Groups, Translation, PlatformTranslation, OrganisationConfig, IdentifierSource, MyGroups, GroupPrivileges, Extension, GroupRole, LocationHierarchy, ReportCard, GroupDashboard, ApprovalStatus, DashboardSectionCardMapping, DashboardFilter, DashboardSection, Dashboard, News, Task, TaskType, UserInfo, Privilege, TaskStatus, StandardReportCardType, TaskUnAssignment, UserSubjectAssignment, Documentation, DocumentationItem, EntityApprovalStatus, ChecklistItemEntityApprovalStatus, MenuItem;

    public static List<SyncEntityName> transactionalEntities = ListUtils.unmodifiableList(Arrays.asList(Subject, Encounter, Comment, ProgramEncounterEntityApprovalStatus, SubjectMigration, ProgramEncounter, Individual, ProgramEnrolment, Encounter, GroupSubject, CommentThread, IndividualRelationship, SubjectEntityApprovalStatus, EncounterEntityApprovalStatus, ProgramEncounterEntityApprovalStatus, ProgramEnrolmentEntityApprovalStatus, ChecklistItemEntityApprovalStatus, SubjectProgramEligibility, Task, Checklist, ChecklistItem, EntityApprovalStatus));

    //EntityApprovalStatus should not be added to it, as it is deprecated now
    public static List<SyncEntityName> approvalStatusEntities = ListUtils.unmodifiableList(Arrays.asList(SubjectEntityApprovalStatus, EncounterEntityApprovalStatus, ProgramEncounterEntityApprovalStatus, ProgramEnrolmentEntityApprovalStatus, ChecklistItemEntityApprovalStatus));

    // These list of entities are sent when mobile app calls for the first time
    public static List<SyncEntityName> getNonTransactionalEntities() {
        return Arrays.stream(SyncEntityName.values()).filter(x -> transactionalEntities.stream().noneMatch(x::equals)).collect(Collectors.toList());
    }

    public boolean nameEquals(String otherName) {
        return this.name().equals(otherName);
    }

    public static boolean existsAsEnum(String str) {
        return Stream.of(SyncEntityName.values()).anyMatch(v -> v.name().equals(str));
    }
}
