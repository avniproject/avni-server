package org.avni.server.application;

import org.avni.server.domain.accessControl.PrivilegeType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum FormType {
    BeneficiaryIdentification,
    IndividualProfile,
    SubjectEnrolmentEligibility,
    ManualProgramEnrolmentEligibility,
    ProgramEnrolment,
    ProgramExit,
    ProgramEncounter,
    ProgramEncounterCancellation,
    Encounter,
    IndividualEncounterCancellation,
    ChecklistItem,
    IndividualRelationship,
    Location,
    Task;

    static FormType[] performEncounterTypes = {Encounter, ProgramEncounter};
    static FormType[] cancelEncounterTypes = {IndividualEncounterCancellation, ProgramEncounterCancellation};
    static FormType[] linkedToEncounterType = {Encounter, ProgramEncounter, ProgramEncounterCancellation, IndividualEncounterCancellation};
    static FormType[] linkedToProgram = {ProgramEncounter, ProgramExit, ProgramEnrolment, ProgramEncounterCancellation};

    private static final Map<FormType, PrivilegeType> PrivilegeTypes = new HashMap<FormType, PrivilegeType>() {{
        put(BeneficiaryIdentification, PrivilegeType.EditSubjectType);
        put(IndividualProfile, PrivilegeType.EditSubjectType);
        put(SubjectEnrolmentEligibility, PrivilegeType.EditSubjectType);
        put(ManualProgramEnrolmentEligibility, PrivilegeType.EditProgram);
        put(ProgramEnrolment, PrivilegeType.EditProgram);
        put(ProgramExit, PrivilegeType.EditProgram);
        put(ProgramEncounter, PrivilegeType.EditEncounterType);
        put(ProgramEncounterCancellation, PrivilegeType.EditEncounterType);
        put(Encounter, PrivilegeType.EditEncounterType);
        put(IndividualEncounterCancellation, PrivilegeType.EditEncounterType);
        put(ChecklistItem, PrivilegeType.EditChecklist);
        put(IndividualRelationship, PrivilegeType.EditSubject);
        put(Location, PrivilegeType.EditLocationType);
        put(Task, PrivilegeType.EditTaskType);
    }};

    public static PrivilegeType getPrivilegeType(String formTypeName) {
        return FormType.getPrivilegeType(FormType.valueOf(formTypeName));
    }

    public static PrivilegeType getPrivilegeType(FormType formType) {
        return PrivilegeTypes.get(formType);
    }

    public static PrivilegeType getPrivilegeType(Form form) {
        return FormType.getPrivilegeType(form.getFormType());
    }

    private boolean isIn(FormType[] formTypes) {
        return Arrays.asList(formTypes).contains(this);
    }

    public boolean isLinkedToEncounterType() {
        return this.isIn(linkedToEncounterType);
    }

    public boolean isLinkedToProgram() {
        return this.isIn(linkedToProgram);
    }

    public boolean isPerformEncounterType() {
        return this.isIn(performEncounterTypes);
    }

    public boolean isCancelEncounterType() {
        return this.isIn(cancelEncounterTypes);
    }
}
