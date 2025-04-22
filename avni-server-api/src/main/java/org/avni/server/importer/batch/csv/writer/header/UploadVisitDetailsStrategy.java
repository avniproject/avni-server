package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class UploadVisitDetailsStrategy implements EncounterHeaderStrategy {

    @Override
    public List<HeaderField> generateHeaders(FormMapping formMapping) {
        List<HeaderField> fields = new ArrayList<>();
        boolean isProgramEncounter = formMapping.getType() == FormType.ProgramEncounter;

        fields.add(new HeaderField(EncounterHeadersCreator.ID, "Optional. Can be used to later identify the entry.", false, null, null, null));
        fields.add(new HeaderField(EncounterHeadersCreator.ENCOUNTER_TYPE_HEADER, formMapping.getEncounterType().getName(), true, null, null, null, false));

        String idField = isProgramEncounter ? EncounterHeadersCreator.PROGRAM_ENROLMENT_ID : EncounterHeadersCreator.SUBJECT_ID;
        String idDescription = "Mandatory. Mention identifier from previous system or UUID of the " +
                (isProgramEncounter ? "program enrolment" : "subject") +
                ". UUID can be identified from address bar in Data Entry App or Longitudinal export file";

        fields.add(new HeaderField(idField, idDescription, true, null, null, null));

        fields.add(new HeaderField(EncounterHeadersCreator.VISIT_DATE, "", false, null, "Format: DD-MM-YYYY", null));
        fields.add(new HeaderField(EncounterHeadersCreator.ENCOUNTER_LOCATION, "", false, null, "Format: latitude,longitude in decimal degrees (e.g., 19.8188,83.9172)", null));
        fields.addAll(AbstractHeaders.generateConceptFields(formMapping));

        return fields;
    }
}