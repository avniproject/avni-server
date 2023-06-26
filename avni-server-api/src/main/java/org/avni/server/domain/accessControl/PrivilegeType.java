package org.avni.server.domain.accessControl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum PrivilegeType {
    ViewSubject, RegisterSubject, EditSubject, VoidSubject, EnrolSubject, ViewEnrolmentDetails, EditEnrolmentDetails, ExitEnrolment, ViewVisit, ScheduleVisit, PerformVisit, EditVisit, CancelVisit, ViewChecklist, EditChecklist, AddMember, EditMember, RemoveMember, ApproveSubject, ApproveEnrolment, ApproveEncounter, ApproveChecklistitem, RejectSubject, RejectEnrolment, RejectEncounter, VoidVisit, RejectChecklistitem, EditSubjectType, EditProgram, EditEncounterType, EditForm, EditConcept, EditOrganisationConfiguration, DeleteOrganisationConfiguration, UploadMetadataAndData, DownloadBundle, EditChecklistConfiguration, EditRelation, EditDocumentation, EditOfflineDashboardAndReportCard, EditApplicationMenu, EditExtension, EditRuleFailure, EditLocationType, EditLocation, EditCatchment, EditUserConfiguration, EditUserGroup, EditIdentifierSource, EditIdentifierUserAssignment, EditTask, DeleteTask;

    private final static List<PrivilegeType> GroupSubject = new ArrayList<PrivilegeType>() {{
        addAll(Arrays.asList(AddMember, EditMember, RemoveMember));
    }};

    public final static List<PrivilegeType> NonTransaction = new ArrayList<PrivilegeType>() {{
        addAll(Arrays.asList(EditSubjectType, EditProgram, EditEncounterType, EditForm, EditConcept, EditOrganisationConfiguration, DeleteOrganisationConfiguration, UploadMetadataAndData, DownloadBundle, EditChecklistConfiguration, EditRelation, EditDocumentation, EditOfflineDashboardAndReportCard, EditApplicationMenu, EditExtension, EditRuleFailure, EditLocationType, EditLocation, EditCatchment, EditUserConfiguration, EditUserGroup, EditIdentifierSource, EditIdentifierUserAssignment));
    }};

    public boolean isForGroupSubject() {
        return GroupSubject.contains(this);
    }
}
