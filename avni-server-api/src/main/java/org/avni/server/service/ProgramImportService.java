package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeaders;
import org.springframework.stereotype.Service;

import static org.avni.server.service.ImportLocationsConstants.STRING_CONSTANT_SEPARATOR;

@Service
public class ProgramImportService implements SampleFileExport {

    private final ImportHelperService importHelperService;
    private final FormMappingRepository formMappingRepository;
    private final ProgramEnrolmentHeaders programEnrolmentHeaders;

    public ProgramImportService(
            ImportHelperService importHelperService,
            FormMappingRepository formMappingRepository,
            ProgramEnrolmentHeaders programEnrolmentHeaders) {
        this.importHelperService = importHelperService;
        this.formMappingRepository = formMappingRepository;
        this.programEnrolmentHeaders = programEnrolmentHeaders;
    }

    @Override
    public String generateSampleFile(String[] uploadSpec) {
        try {
            FormMapping formMapping = getFormMapping(uploadSpec);

            StringBuilder sampleFileBuilder = new StringBuilder();
            sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, programEnrolmentHeaders.getAllHeaders(formMapping))).append("\n");
            sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, programEnrolmentHeaders.getAllDescriptions(formMapping))).append("\n");
            return sampleFileBuilder.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating sample CSV", e);
        }
    }

    public FormMapping getFormMapping(String[] uploadSpec) {
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(importHelperService.getSubjectType(uploadSpec[2]).getUuid(), importHelperService.getProgram(uploadSpec[1]).getUuid(), null, FormType.ProgramEnrolment);
        importHelperService.assertNotNull(formMapping, "Form mapping");
        return formMapping;
    }
}