package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.dao.application.FormMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProgramEnrolmentHeadersCreator extends AbstractHeaders {
    public final static String id = "Id from previous system";
    public final static String subjectId = "Subject Id from previous system";
    public final static String programHeader = "Program";
    public final static String enrolmentDate = "Enrolment Date";
    public final static String enrolmentCoordinates = "Enrolment Coordinates";
    public final static String exitDate = "Exit Date";
    public final static String exitCoordinates = "Exit Coordinates";
    public final static String EXIT_CONCEPT_PREFIX = "Exit: ";

    private final FormMappingRepository formMappingRepository;

    @Autowired
    public ProgramEnrolmentHeadersCreator(FormMappingRepository formMappingRepository) {
        this.formMappingRepository = formMappingRepository;
    }

    @Override
    protected List<HeaderField> buildFields(FormMapping enrolmentFormMapping, Object mode) {
        ProgramEnrolmentUploadMode enrolmentMode = mode instanceof ProgramEnrolmentUploadMode
                ? (ProgramEnrolmentUploadMode) mode
                : ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT;

        List<HeaderField> fields = new ArrayList<>();

        fields.add(new HeaderField(id, "Can be used to later identify the entry", false, null, null, null));
        fields.add(new HeaderField(subjectId, "Subject id used in subject upload or UUID of subject (can be identified from address bar in Data Entry App or Longitudinal export file)", true, null, null, null));
        fields.add(new HeaderField(programHeader, enrolmentFormMapping.getProgram().getName(), true, null, null, null, true));
        fields.add(new HeaderField(enrolmentDate, "", true, null, "Format: DD-MM-YYYY or YYYY-MM-DD", null));
        fields.add(new HeaderField(enrolmentCoordinates, "", false, null, "Format: latitude,longitude in decimal degrees (e.g., 19.8188,83.9172)", null));

        fields.addAll(generateConceptFields(enrolmentFormMapping, false));
        fields.addAll(generateDecisionConceptFields(enrolmentFormMapping.getForm()));

        if (enrolmentMode == ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT) {
            fields.add(new HeaderField(exitDate, "Date the enrolment was exited; not in future and on or after Enrolment Date", true, null, "Format: DD-MM-YYYY or YYYY-MM-DD", null));
            fields.add(new HeaderField(exitCoordinates, "", false, null, "Format: latitude,longitude in decimal degrees (e.g., 19.8188,83.9172)", null));

            FormMapping programExitFormMapping = formMappingRepository.getProgramExitFormMapping(
                    enrolmentFormMapping.getSubjectType(),
                    enrolmentFormMapping.getProgram());
            if (programExitFormMapping != null) {
                fields.addAll(generateConceptFields(programExitFormMapping, false, EXIT_CONCEPT_PREFIX));
                fields.addAll(generateDecisionConceptFields(programExitFormMapping.getForm(), EXIT_CONCEPT_PREFIX));
            }
        }

        return fields;
    }
}
