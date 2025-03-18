package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.service.ImportHelperService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProgramEnrolmentHeadersCreator extends AbstractHeaders{
    public final static String id = "Id from previous system";
    public final static String subjectId = "Subject Id";
    public final static String programHeader = "Program";
    public final static String enrolmentDate = "Enrolment Date";
    public final static String exitDate = "Exit Date";
    public final static String enrolmentLocation = "Enrolment Location";
    public final static String exitLocation = "Exit Location";

    private final FormMappingRepository formMappingRepository;

    public ProgramEnrolmentHeadersCreator(
            ImportHelperService importHelperService,
            FormMappingRepository formMappingRepository) {
        super(importHelperService);
        this.formMappingRepository = formMappingRepository;
    }

    @Override
    protected List<HeaderField> buildFields(FormMapping formMapping) {
        List<HeaderField> fields = new ArrayList<>();

        fields.add(new HeaderField(id, "Can be used to later identify the entry", false, null, null, null));
        fields.add(new HeaderField(subjectId, "UUID of the subject to be enrolled. Can be identified from address bar in Data Entry App or Longitudinal export file", true, null, null, null));
        if(formMappingRepository.getProgramsMappedToAForm(formMapping.getForm().getUuid()).size() > 1) {
            fields.add(new HeaderField(programHeader, formMapping.getProgram().getName() , false, null, null, null));
        }
        fields.add(new HeaderField(enrolmentDate, "", false, null, "Format: DD-MM-YYYY", null));
        fields.add(new HeaderField(exitDate, "", false, null, "Format: DD-MM-YYYY", null));
        fields.add(new HeaderField(enrolmentLocation, "", false, null, "Format: (21.5135243,85.6731848)", null));
        fields.add(new HeaderField(exitLocation, "", false, null, "Format: (21.5135243,85.6731848)", null));

        fields.addAll(generateConceptFields(formMapping));

        return fields;
    }

}
