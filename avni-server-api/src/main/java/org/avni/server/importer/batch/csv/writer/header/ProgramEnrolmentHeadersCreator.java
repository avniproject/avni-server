package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProgramEnrolmentHeadersCreator extends AbstractHeaders {
    public final static String id = "Id from previous system";
    public final static String subjectId = "Subject Id from previous system";
    public final static String programHeader = "Program";
    public final static String enrolmentDate = "Enrolment Date";
    public final static String enrolmentLocation = "Enrolment Location";

    @Override
    protected List<HeaderField> buildFields(FormMapping formMapping) {
        List<HeaderField> fields = new ArrayList<>();

        fields.add(new HeaderField(id, "Can be used to later identify the entry", false, null, null, null));
        fields.add(new HeaderField(subjectId, "Subject id used in subject upload or UUID of subject (can be identified from address bar in Data Entry App or Longitudinal export file)", true, null, null, null));
        fields.add(new HeaderField(programHeader, formMapping.getProgram().getName(), true, null, null, null, false));
        fields.add(new HeaderField(enrolmentDate, "", false, null, "Format: DD-MM-YYYY or YYYY-MM-DD", null));
        fields.add(new HeaderField(enrolmentLocation, "", false, null, "Format: (21.5135243,85.6731848)", null));

        fields.addAll(generateConceptFields(formMapping));
        fields.addAll(generateDecisionConceptFields(formMapping.getForm()));

        return fields;
    }

}
