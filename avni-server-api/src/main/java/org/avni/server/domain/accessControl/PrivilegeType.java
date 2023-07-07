package org.avni.server.domain.accessControl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum PrivilegeType {
    AddMember, ApproveChecklistitem, ApproveEncounter, ApproveEnrolment, ApproveSubject, CancelVisit, DeleteOrganisationConfiguration, DeleteTask, DownloadBundle, EditApplicationMenu, EditCatchment, EditChecklist, EditChecklistConfiguration, EditConcept, EditDocumentation, EditEncounterType, EditEnrolmentDetails, EditExtension, EditForm, EditIdentifierSource, EditIdentifierUserAssignment, EditLanguage, EditLocation, EditLocationType, EditMember, EditOfflineDashboardAndReportCard, EditOrganisationConfiguration, EditProgram, EditRelation, EditRuleFailure, EditSubject, EditSubjectType, EditTask, EditUserConfiguration, EditUserGroup, EditVisit, EnrolSubject, ExitEnrolment, PerformVisit, PhoneVerification, RegisterSubject, RejectChecklistitem, RejectEncounter, RejectEnrolment, RejectSubject, RemoveMember, ScheduleVisit, UploadMetadataAndData, ViewChecklist, ViewEnrolmentDetails, ViewSubject, ViewVisit, VoidSubject, VoidVisit;

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
