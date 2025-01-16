package org.avni.server.common.dbSchema;

import org.avni.server.application.FormType;

import java.util.HashMap;
import java.util.Map;

public class TableNames {
    public static final String Subject = "individual";
    public static final String ProgramEnrolment = "program_enrolment";
    public static final String ProgramEncounter = "program_encounter";
    public static final String Encounter = "encounter";
    public static final String SubjectProgramEligibility = "subject_program_eligibility";

    private static final Map<FormType, String> formTypeTableMap = new HashMap<FormType, String>() {{
        put(FormType.IndividualProfile, Subject);
        put(FormType.ProgramEnrolment, ProgramEnrolment);
        put(FormType.ProgramExit, ProgramEnrolment);
        put(FormType.ProgramEncounter, ProgramEncounter);
        put(FormType.ProgramEncounterCancellation, ProgramEncounter);
        put(FormType.Encounter, Encounter);
        put(FormType.IndividualEncounterCancellation, Encounter);
        put(FormType.SubjectEnrolmentEligibility, SubjectProgramEligibility);
    }};

    public static String getTableName(FormType formType) {
        return formTypeTableMap.get(formType);
    }
}
