package org.avni.server.common.dbSchema;

import org.avni.server.application.FormType;

import java.util.*;

public class ColumnNames {
    public static final String IndividualObservations = "observations";
    public static final String EncounterObservations = "observations";
    public static final String EncounterCancelObservations = "cancel_observations";
    public static final String ProgramEnrolmentObservations = "observations";
    public static final String ProgramEnrolmentExitObservations = "program_exit_observations";

    public static final String EncounterDateTime = "encounter_date_time";
    public static final String EncounterCancelDateTime = "cancel_date_time";
    public static final String RegistrationDate = "registration_date";
    public static final String EnrolmentDateTime = "enrolment_date_time";
    public static final String ProgramExitDateTime = "program_exit_date_time";

    private static final Map<FormType, String> obsColumnMap = new HashMap<FormType, String>() {{
        put(FormType.IndividualProfile, IndividualObservations);

        put(FormType.ProgramEnrolment, ProgramEnrolmentObservations);
        put(FormType.ProgramExit, ProgramEnrolmentExitObservations);

        put(FormType.ProgramEncounter, EncounterObservations);
        put(FormType.ProgramEncounterCancellation, EncounterCancelObservations);

        put(FormType.Encounter, EncounterObservations);
        put(FormType.IndividualEncounterCancellation, EncounterCancelObservations);
    }};

    private static final Map<FormType, String> eventOccurrenceColumns = new HashMap<FormType, String>() {{
        put(FormType.IndividualProfile, RegistrationDate);
        put(FormType.ProgramEnrolment, EnrolmentDateTime);
        put(FormType.ProgramExit, ProgramExitDateTime);
        put(FormType.ProgramEncounter, EncounterDateTime);
        put(FormType.ProgramEncounterCancellation, EncounterCancelDateTime);
        put(FormType.Encounter, EncounterDateTime);
        put(FormType.IndividualEncounterCancellation, EncounterCancelDateTime);
    }};

    public static String getObsColumn(FormType formType) {
        return obsColumnMap.get(formType);
    }

    public static String getOccurrenceDateTimeColumn(FormType formType) {
        return eventOccurrenceColumns.get(formType);
    }
}
