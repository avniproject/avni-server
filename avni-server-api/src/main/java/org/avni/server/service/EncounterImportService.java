package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.SubjectType;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.EncounterUploadMode;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.importer.batch.csv.writer.header.Mode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.avni.server.service.ImportLocationsConstants.STRING_CONSTANT_SEPARATOR;

@Service
public class EncounterImportService extends AbstractSampleFileExportService {

    private final FormMappingRepository formMappingRepository;
    private final EncounterHeadersCreator encounterHeadersCreator;
    private final SubjectTypeRepository subjectTypeRepository;
    private final EncounterTypeRepository encounterTypeRepository;

    @Autowired
    public EncounterImportService(FormMappingRepository formMappingRepository,
                                  SubjectTypeRepository subjectTypeRepository,
                                  EncounterTypeRepository encounterTypeRepository,
                                  EncounterHeadersCreator encounterHeadersCreator) {
        this.formMappingRepository = formMappingRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.encounterHeadersCreator = encounterHeadersCreator;
    }

    @Override
    protected HeaderCreator getHeaders() {
        return encounterHeadersCreator;
    }

    @Override
    public FormMapping getFormMapping(String[] uploadSpec) {
        FormType formType = uploadSpec[0].equals("Encounter") ? FormType.Encounter : FormType.ProgramEncounter;
        return formMappingRepository.getRequiredFormMapping(
                getSubjectType(uploadSpec[2]).getUuid(),
                null,
                getEncounterType(uploadSpec[1]).getUuid(),
                formType);
    }

    @Override
    public String generateSampleFile(String[] uploadSpec, Mode mode) {
        EncounterUploadMode encounterMode = (EncounterUploadMode) mode;
        FormMapping formMapping = getFormMapping(uploadSpec);
        StringBuilder sampleFileBuilder = new StringBuilder();
        sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, getHeaders().getAllHeaders(formMapping, encounterMode))).append("\n");
        sampleFileBuilder.append(String.join(STRING_CONSTANT_SEPARATOR, getHeaders().getAllDescriptions(formMapping, encounterMode))).append("\n");
        return sampleFileBuilder.toString();
    }

    private SubjectType getSubjectType(String subjectTypeName) {
        return subjectTypeRepository.findByName(subjectTypeName);
    }

    private EncounterType getEncounterType(String encounterTypeName) {
        return encounterTypeRepository.findByName(encounterTypeName);
    }
}