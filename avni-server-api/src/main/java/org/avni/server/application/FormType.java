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

    static final FormType[] performEncounterTypes = {Encounter, ProgramEncounter};
    static final FormType[] cancelEncounterTypes = {IndividualEncounterCancellation, ProgramEncounterCancellation};
    static final FormType[] linkedToEncounterType = {Encounter, ProgramEncounter, ProgramEncounterCancellation, IndividualEncounterCancellation};
    static final FormType[] linkedToProgram = {ProgramEncounter, ProgramExit, ProgramEnrolment, ProgramEncounterCancellation};

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
        put(ChecklistItem, PrivilegeType.EditChecklistConfiguration);
        put(IndividualRelationship, PrivilegeType.EditSubject);
        put(Location, PrivilegeType.EditLocationType);
        put(Task, PrivilegeType.EditTaskType);
    }};

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
}
