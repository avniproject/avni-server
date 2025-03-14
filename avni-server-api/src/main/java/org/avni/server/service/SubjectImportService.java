package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.avni.server.service.ImportLocationsConstants.STRING_CONSTANT_SEPARATOR;

@Service
public class SubjectImportService implements SampleFileExport {

    private final ImportHelperService importHelperService;
    private final FormMappingRepository formMappingRepository;
    private final SubjectHeaders subjectHeaders;

    public SubjectImportService(
            ImportHelperService importHelperService,
            FormMappingRepository formMappingRepository,
            SubjectHeaders subjectHeaders) {
        this.importHelperService = importHelperService;
        this.formMappingRepository = formMappingRepository;
        this.subjectHeaders = subjectHeaders;
    }

    @Override
    public String generateSampleFile(String[] uploadSpec) {
        try {
            FormMapping formMapping = getFormMapping(uploadSpec);

            StringBuilder sampleFileBuilder = new StringBuilder();
            sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, subjectHeaders.getAllHeaders(formMapping.getSubjectType(),formMapping))).append("\n");
            sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, subjectHeaders.getAllDescriptions(formMapping.getSubjectType(),formMapping))).append("\n");
            return sampleFileBuilder.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating sample CSV", e);
        }
    }

    private FormMapping getFormMapping(String[] uploadSpec) {
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(
                importHelperService.getSubjectType(uploadSpec[1]).getUuid(),
                null, null, FormType.IndividualProfile);
        importHelperService.assertNotNull(formMapping, "Form mapping");
        return formMapping;
    }
}