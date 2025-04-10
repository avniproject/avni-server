package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentHeadersCreator;
import org.springframework.stereotype.Service;

@Service
public class ProgramImportService extends AbstractSampleFileExportService {
    private final ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator;
    private final ProgramRepository programRepository;
    private final SubjectTypeRepository subjectTypeRepository;

    public ProgramImportService(
            FormMappingRepository formMappingRepository,
            ProgramEnrolmentHeadersCreator programEnrolmentHeadersCreator,
            ProgramRepository programRepository,
            SubjectTypeRepository subjectTypeRepository) {
        super(formMappingRepository);
        this.programEnrolmentHeadersCreator = programEnrolmentHeadersCreator;
        this.programRepository = programRepository;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    @Override
    protected HeaderCreator getHeaders() {
        return programEnrolmentHeadersCreator;
    }

    @Override
    public FormMapping getFormMapping(String[] uploadSpec) {
        return formMappingRepository.getRequiredFormMapping(subjectTypeRepository.findByName(uploadSpec[2]).getUuid(), programRepository.findByName(uploadSpec[1]).getUuid(), null, FormType.ProgramEnrolment);
    }
}
