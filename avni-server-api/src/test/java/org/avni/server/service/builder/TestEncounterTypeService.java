package org.avni.server.service.builder;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.OperationalEncounterTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestEncounterTypeService {
    private final FormRepository formRepository;
    private final FormMappingRepository formMappingRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final OperationalEncounterTypeRepository operationalEncounterTypeRepository;

    @Autowired
    public TestEncounterTypeService(FormRepository formRepository, FormMappingRepository formMappingRepository, EncounterTypeRepository encounterTypeRepository, OperationalEncounterTypeRepository operationalEncounterTypeRepository) {
        this.formRepository = formRepository;
        this.formMappingRepository = formMappingRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.operationalEncounterTypeRepository = operationalEncounterTypeRepository;
    }

    public FormMapping addGeneralEncounterTypeAndGetFormMapping(String encounterTypeName, SubjectType subjectType) {
        EncounterType encounterType = new EncounterTypeBuilder().withName(encounterTypeName).build();
        encounterTypeRepository.save(encounterType);
        OperationalEncounterType operationalEncounterType = new OperationalEncounterType();
        operationalEncounterType.setName(encounterTypeName);
        operationalEncounterType.setEncounterType(encounterType);
        operationalEncounterType.setUuid(UUID.randomUUID().toString());
        operationalEncounterTypeRepository.save(operationalEncounterType);

        Form form = new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(FormType.Encounter).build();
        formRepository.save(form);

        return formMappingRepository.saveFormMapping(new FormMappingBuilder().withForm(form).withEncounterType(encounterType)
                .withSubjectType(subjectType).build());
    }

    public FormMapping addProgramEncounterTypeAndGetFormMapping(String encounterTypeName, SubjectType subjectType, Program program) {
        EncounterType encounterType = new EncounterTypeBuilder().withName(encounterTypeName).build();
        encounterTypeRepository.save(encounterType);
        OperationalEncounterType operationalEncounterType = new OperationalEncounterType();
        operationalEncounterType.setName(encounterTypeName);
        operationalEncounterType.setEncounterType(encounterType);
        operationalEncounterType.setUuid(UUID.randomUUID().toString());
        operationalEncounterTypeRepository.save(operationalEncounterType);

        Form form = new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(FormType.ProgramEncounter).build();
        formRepository.save(form);

        return formMappingRepository.saveFormMapping(new FormMappingBuilder().withForm(form).withEncounterType(encounterType).withProgram(program)
                .withSubjectType(subjectType).build());
    }
}
