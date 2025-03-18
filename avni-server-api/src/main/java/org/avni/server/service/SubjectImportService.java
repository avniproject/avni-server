package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.importer.batch.csv.writer.header.Headers;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeadersCreator;
import org.springframework.stereotype.Service;

@Service
public class SubjectImportService extends AbstractSampleFileExportService {

    private final SubjectHeadersCreator subjectHeadersCreator;

    public SubjectImportService(
            ImportHelperService importHelperService,
            FormMappingRepository formMappingRepository,
            SubjectHeadersCreator subjectHeadersCreator) {
        super(importHelperService, formMappingRepository);
        this.subjectHeadersCreator = subjectHeadersCreator;
    }

    @Override
    protected Headers getHeaders() {
        return subjectHeadersCreator;
    }

    public FormMapping getFormMapping(String[] uploadSpec) {
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(
                importHelperService.getSubjectType(uploadSpec[1]).getUuid(),
                null, null, FormType.IndividualProfile);
        return formMapping;
    }
}