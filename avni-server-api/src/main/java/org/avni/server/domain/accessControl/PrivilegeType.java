package org.avni.server.domain.accessControl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum PrivilegeType {
    AddMember, ApproveChecklistitem, ApproveEncounter, ApproveEnrolment, ApproveSubject, CancelVisit, DeleteOrganisationConfiguration, DeleteTask, DownloadBundle, EditApplicationMenu, EditCatchment, EditChecklist, EditChecklistConfiguration, EditConcept, EditDocumentation, EditEncounterType, EditEnrolmentDetails, EditExtension, EditForm, EditIdentifierSource, EditIdentifierUserAssignment, EditLanguage, EditLocation, EditLocationType, EditMember, EditOfflineDashboardAndReportCard, EditOrganisationConfiguration, EditProgram, EditRelation, EditRuleFailure, EditSubject, EditSubjectType, EditTask, EditUserConfiguration, EditUserGroup, EditVideo, EditVisit, EnrolSubject, ExitEnrolment, PerformVisit, PhoneVerification, RegisterSubject, RejectChecklistitem, RejectEncounter, RejectEnrolment, RejectSubject, RemoveMember, Analytics, ScheduleVisit, UploadMetadataAndData, ViewChecklist, ViewEnrolmentDetails, ViewSubject, ViewVisit, VoidSubject, VoidVisit, EditNews;

    private final static List<PrivilegeType> GroupSubject = new ArrayList<PrivilegeType>() {{
        addAll(Arrays.asList(AddMember, EditMember, RemoveMember));
    }};

    public boolean isForGroupSubject() {
        return GroupSubject.contains(this);
    }
}
