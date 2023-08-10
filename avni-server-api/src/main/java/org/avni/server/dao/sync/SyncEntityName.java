package org.avni.server.dao.sync;

import org.apache.commons.collections.ListUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum SyncEntityName {
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
    IndividualRelationShip,
    LocationMapping,
    Location,
    SubjectMigration,
    GroupSubject,
    SubjectProgramEligibility,
    IndividualRelationship,
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
    Rule, IndividualRelation, IndividualRelationGenderMapping, IndividualRelationshipType, Program, ProgramOutcome, Gender, Concept, ProgramConfig, Video, SubjectType, ChecklistItemDetail, FormElementGroup, FormElement, ConceptAnswer, Groups, Translation, PlatformTranslation, OrganisationConfig, IdentifierSource, MyGroups, GroupPrivileges, Extension, GroupRole, LocationHierarchy, ReportCard, GroupDashboard, ApprovalStatus, DashboardSectionCardMapping, DashboardFilter, DashboardSection, Dashboard, News, Task, TaskType, UserInfo, Privilege, TaskStatus, StandardReportCardType, TaskUnAssignment, UserSubjectAssignment, Documentation, DocumentationItem, EntityApprovalStatus, ChecklistItemEntityApprovalStatus, MenuItem;

    public static List<SyncEntityName> entitiesWithSubEntity = ListUtils.unmodifiableList(Arrays.asList(Encounter, Comment, ProgramEncounterEntityApprovalStatus, SubjectMigration, ProgramEncounter, Individual, ProgramEnrolment, Encounter, GroupSubject, CommentThread));

    //EntityApprovalStatus should not be added to it, as it is deprecated now
    public static List<SyncEntityName> approvalStatusEntities = ListUtils.unmodifiableList(Arrays.asList(SubjectEntityApprovalStatus, EncounterEntityApprovalStatus, ProgramEncounterEntityApprovalStatus, ProgramEnrolmentEntityApprovalStatus, ChecklistItemEntityApprovalStatus));

    public static List<SyncEntityName> getEntitiesWithoutSubEntity() {
        return Arrays.stream(SyncEntityName.values()).filter(x -> entitiesWithSubEntity.stream().anyMatch(x::equals)).collect(Collectors.toList());
    }

    public boolean nameEquals(String otherName) {
        return this.name().equals(otherName);
    }
}
