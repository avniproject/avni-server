package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.application.FormMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EncounterHeadersCreator extends AbstractHeaders implements HeaderCreator {

    public static final String ID = "Id from previous system";
    public static final String ENCOUNTER_TYPE = "Encounter Type";
    public static final String PROGRAM_ENROLMENT_ID = "Program Enrolment Id";
    public static final String SUBJECT_ID = "Subject Id";
    public static final String VISIT_DATE = "Visit Date";
    public static final String EARLIEST_VISIT_DATE = "Earliest Visit Date";
    public static final String MAX_VISIT_DATE = "Max Visit Date";
    public static final String ENCOUNTER_COORDINATES = "Encounter Coordinates";
    public static final String CANCEL_DATE = "Cancel Date";
    public static final String CANCEL_LOCATION = "Cancel Location";

    private final FormMappingRepository formMappingRepository;

    @Autowired
    public EncounterHeadersCreator(FormMappingRepository formMappingRepository) {
        this.formMappingRepository = formMappingRepository;
    }

    @Override
    @Deprecated
    public String[] getAllHeaders() {
        throw new UnsupportedOperationException("Use getAllHeaders(FormMapping, Mode) instead");
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping, Object mode) {
        return buildFields(formMapping, mode).stream()
                .map(HeaderField::getHeader)
                .toArray(String[]::new);
    }

    @Override
    public String[] getAllDescriptions(FormMapping formMapping, Object mode) {
        return buildFields(formMapping, mode).stream()
                .map(HeaderField::getDescription)
                .toArray(String[]::new);
    }

    @Override
    protected List<HeaderField> buildFields(FormMapping formMapping, Object mode) {
        EncounterUploadMode encounterMode = (EncounterUploadMode) mode;
        EncounterHeadersBuilder builder = new EncounterHeadersBuilder(formMapping);
        switch (encounterMode) {
            case UPLOAD_VISIT_DETAILS:
                return builder.withVisitDetails().build();
            case SCHEDULE_VISIT:
                return builder.withScheduleWindow().build();
            case UPLOAD_CANCELLED_VISIT:
                return builder.withCancelDetails(resolveCancellationFormMapping(formMapping)).build();
            default:
                throw new IllegalArgumentException("Unsupported EncounterUploadMode: " + encounterMode);
        }
    }

    private FormMapping resolveCancellationFormMapping(FormMapping encounterFormMapping) {
        if (encounterFormMapping.getType() == FormType.ProgramEncounter) {
            return formMappingRepository.getProgramEncounterCancelFormMapping(
                    encounterFormMapping.getSubjectType(),
                    encounterFormMapping.getProgram(),
                    encounterFormMapping.getEncounterType());
        }
        return formMappingRepository.getGeneralEncounterCancelFormMapping(
                encounterFormMapping.getSubjectType(),
                encounterFormMapping.getEncounterType());
    }
}
