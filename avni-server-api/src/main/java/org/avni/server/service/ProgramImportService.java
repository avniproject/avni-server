package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeadersCreator;
import org.springframework.stereotype.Service;


@Service
public class ProgramImportService extends AbstractSampleFileExportService {

    private final ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;

    public ProgramImportService(
            ImportHelperService importHelperService,
            FormMappingRepository formMappingRepository,
            ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator) {
        super(importHelperService, formMappingRepository);
        this.programEnrolmentHeadersCreator = programEnrolmentHeadersCreator;
    }

    @Override
    protected HeaderCreator getHeaders() {
        return programEnrolmentHeadersCreator;
    }

    @Override
    public FormMapping getFormMapping(String[] uploadSpec) {
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(importHelperService.getSubjectType(uploadSpec[2]).getUuid(), importHelperService.getProgram(uploadSpec[1]).getUuid(), null, FormType.ProgramEnrolment);
        return formMapping;
    }
}
