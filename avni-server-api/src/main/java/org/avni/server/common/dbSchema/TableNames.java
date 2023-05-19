package org.avni.server.common.dbSchema;

import org.avni.server.application.FormType;

import java.util.HashMap;
import java.util.Map;

public class TableNames {
    public static final String Subject = "individual";
    public static final String ProgramEnrolment = "program_enrolment";
    public static final String ProgramEncounter = "program_encounter";
    public static final String Encounter = "encounter";

    private static final Map<FormType, String> formTypeTableMap = new HashMap<FormType, String>() {{
        put(FormType.IndividualProfile, Subject);
        put(FormType.ProgramEnrolment, ProgramEnrolment);
        put(FormType.ProgramEncounter, ProgramEncounter);
        put(FormType.Encounter, Encounter);
    }};

    public static String getTableName(FormType formType) {
        return formTypeTableMap.get(formType);
    }
}
