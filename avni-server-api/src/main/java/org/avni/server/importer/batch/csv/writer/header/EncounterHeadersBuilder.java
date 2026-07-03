package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;

import java.util.ArrayList;
import java.util.List;

public class EncounterHeadersBuilder {
    private static final String FLEXIBLE_DATE_FORMAT = "Format: DD-MM-YYYY or YYYY-MM-DD";
    private static final String COORDINATES_FORMAT = "Format: latitude,longitude in decimal degrees (e.g., 19.8188,83.9172)";

    private final FormMapping encounterFormMapping;
    private final List<HeaderField> fields = new ArrayList<>();

    public EncounterHeadersBuilder(FormMapping encounterFormMapping) {
        this.encounterFormMapping = encounterFormMapping;
        addCommonHeaders();
    }

    private void addCommonHeaders() {
        boolean isProgramEncounter = encounterFormMapping.getType() == FormType.ProgramEncounter;

        fields.add(new HeaderField(EncounterHeadersCreator.ID, "Optional. Can be used to later identify the entry.", false, null, null, null));
        fields.add(new HeaderField(EncounterHeadersCreator.ENCOUNTER_TYPE, encounterFormMapping.getEncounterType().getName(), true, null, null, null, true));

        String idHeader = isProgramEncounter ? EncounterHeadersCreator.PROGRAM_ENROLMENT_ID : EncounterHeadersCreator.SUBJECT_ID;
        String idDescription = "Mention identifier from previous system or UUID of the " +
                (isProgramEncounter ? "program enrolment" : "subject") +
                ". UUID can be identified from address bar in Data Entry App or Longitudinal export file.";
        fields.add(new HeaderField(idHeader, idDescription, true, null, null, null));
    }

    public EncounterHeadersBuilder withScheduleWindow() {
        addScheduleWindow(true, "", "");
        return this;
    }

    public EncounterHeadersBuilder withVisitDetails() {
        addScheduleWindow(false,
                "Original schedule window start (if known). Not cross-checked against Visit Date.",
                "Original schedule window end (if known); on or after Earliest Visit Date. Not cross-checked against Visit Date.");
        fields.add(new HeaderField(EncounterHeadersCreator.VISIT_DATE, "", true, null, FLEXIBLE_DATE_FORMAT, null));
        fields.add(new HeaderField(EncounterHeadersCreator.ENCOUNTER_COORDINATES, "", false, null, COORDINATES_FORMAT, null));
        fields.addAll(AbstractHeaders.generateConceptFields(encounterFormMapping, false));
        fields.addAll(AbstractHeaders.generateDecisionConceptFields(encounterFormMapping.getForm()));
        return this;
    }

    public EncounterHeadersBuilder withCancelDetails(FormMapping cancellationFormMapping) {
        addScheduleWindow(true,
                "Original schedule window start from source system",
                "Original schedule window end; on or after Earliest Visit Date");
        fields.add(new HeaderField(EncounterHeadersCreator.CANCEL_DATE, "Date of cancellation; cannot be in future and must be on or after Earliest Visit Date", true, null, FLEXIBLE_DATE_FORMAT, null));
        fields.add(new HeaderField(EncounterHeadersCreator.CANCEL_LOCATION, "", false, null, COORDINATES_FORMAT, null));
        if (cancellationFormMapping != null) {
            fields.addAll(AbstractHeaders.generateConceptFields(cancellationFormMapping, false));
            fields.addAll(AbstractHeaders.generateDecisionConceptFields(cancellationFormMapping.getForm()));
        }
        return this;
    }

    private void addScheduleWindow(boolean mandatory, String earliestDescription, String maxDescription) {
        fields.add(new HeaderField(EncounterHeadersCreator.EARLIEST_VISIT_DATE, earliestDescription, mandatory, null, FLEXIBLE_DATE_FORMAT, null));
        fields.add(new HeaderField(EncounterHeadersCreator.MAX_VISIT_DATE, maxDescription, mandatory, null, FLEXIBLE_DATE_FORMAT, null));
    }

    public List<HeaderField> build() {
        return fields;
    }
}
