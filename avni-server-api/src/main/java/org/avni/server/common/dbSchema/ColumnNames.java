package org.avni.server.common.dbSchema;

import org.avni.server.application.FormType;

import java.util.*;

public class ColumnNames {
    public static final String IndividualObservations = "observations";
    public static final String EncounterObservations = "observations";
    public static final String EncounterCancelObservations = "cancel_observations";
    public static final String ProgramEnrolmentObservations = "observations";
    public static final String ProgramEnrolmentExitObservations = "program_exit_observations";

    private static final Map<FormType, String> obsColumnMap = new HashMap<FormType, String>() {{
        put(FormType.IndividualProfile, IndividualObservations);
        put(FormType.ProgramEnrolment, ProgramEnrolmentObservations);
        put(FormType.ProgramExit, ProgramEnrolmentExitObservations);
        put(FormType.ProgramEncounter, EncounterObservations);
        put(FormType.ProgramEncounterCancellation, EncounterCancelObservations);
    }};

    public static String getObsColumn(FormType formType) {
        return obsColumnMap.get(formType);
    }
}
