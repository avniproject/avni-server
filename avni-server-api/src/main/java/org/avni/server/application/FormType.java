package org.avni.server.application;

import java.util.Arrays;

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
