package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeadersCreator;
import org.springframework.stereotype.Service;

@Service
public class SubjectImportService extends AbstractSampleFileExportService {
    private final FormMappingRepository formMappingRepository;
    private final SubjectHeadersCreator subjectHeadersCreator;
    private final SubjectTypeRepository subjectTypeRepository;

    public SubjectImportService(
            FormMappingRepository formMappingRepository,
            SubjectHeadersCreator subjectHeadersCreator,
            SubjectTypeRepository subjectTypeRepository) {
        this.formMappingRepository = formMappingRepository;
        this.subjectHeadersCreator = subjectHeadersCreator;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    @Override
    protected HeaderCreator getHeaders() {
        return subjectHeadersCreator;
    }

    public FormMapping getFormMapping(String[] uploadSpec) {
        return formMappingRepository.getRequiredFormMapping(
                subjectTypeRepository.findByName(uploadSpec[1]).getUuid(),
                null, null, FormType.IndividualProfile);
    }
}
