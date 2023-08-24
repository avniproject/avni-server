package org.avni.server.domain.accessControl;

import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivilege;

import java.util.ArrayList;
import java.util.List;

public class GroupPrivileges {
    private final boolean hasAllPrivileges;
    private final List<GroupPrivilege> privileges;

    public GroupPrivileges(boolean hasAllPrivileges, List<GroupPrivilege> privileges) {
        this.hasAllPrivileges = hasAllPrivileges;
        this.privileges = privileges;
    }

    public GroupPrivileges() {
        this.hasAllPrivileges = true;
        this.privileges = new ArrayList<>();
    }

    public boolean hasPrivilege(PrivilegeType privilegeType, SubjectType subjectType, Program program, EncounterType encounterType, ChecklistDetail checklistDetail) {
        return this.hasAllPrivileges || privileges.stream().anyMatch(groupPrivilege -> groupPrivilege.matches(privilegeType, subjectType, program, encounterType, checklistDetail));
    }

    public boolean hasViewPrivilege(ChecklistItem checklistItem) {
        return this.hasViewPrivilege(checklistItem.getChecklist());
    }

    public boolean hasViewPrivilege(ProgramEncounter programEncounter) {
        return this.hasPrivilege(PrivilegeType.ViewVisit,
                programEncounter.getProgramEnrolment().getIndividual().getSubjectType(),
                programEncounter.getProgramEnrolment().getProgram(),
                null, null
        );
    }

    public boolean hasViewPrivilege(Encounter encounter) {
        return this.hasPrivilege(PrivilegeType.ViewVisit,
                encounter.getIndividual().getSubjectType(),
                null, null, null
        );
    }

    public boolean hasViewPrivilege(Individual individual) {
        return this.hasPrivilege(PrivilegeType.ViewSubject, individual.getSubjectType(),
                null, null, null
        );
    }
    public boolean hasViewPrivilege(ProgramEnrolment programEnrolment) {
        return this.hasPrivilege(PrivilegeType.ViewEnrolmentDetails,
                programEnrolment.getIndividual().getSubjectType(),
                programEnrolment.getProgram(),
                null, null
        );
    }

    public boolean hasViewPrivilege(Checklist checklist) {
        return this.hasPrivilege(PrivilegeType.ViewChecklist,
                checklist.getProgramEnrolment().getIndividual().getSubjectType(),
                null,
                null, checklist.getChecklistDetail()
        );
    }
    public List<GroupPrivilege> getPrivileges() {
        return privileges;
    }
}
