package org.avni.server.service.builder;

import org.avni.server.application.Form;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestSubjectTypeService {
    private final SubjectTypeRepository subjectTypeRepository;
    private final OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    private final FormRepository formRepository;
    private final FormMappingRepository formMappingRepository;

    @Autowired
    public TestSubjectTypeService(SubjectTypeRepository subjectTypeRepository, OperationalSubjectTypeRepository operationalSubjectTypeRepository, FormRepository formRepository, FormMappingRepository formMappingRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.operationalSubjectTypeRepository = operationalSubjectTypeRepository;
        this.formRepository = formRepository;
        this.formMappingRepository = formMappingRepository;
    }

    public void create(OperationalSubjectType operationalSubjectType, SubjectType subjectType) {
        operationalSubjectType.setSubjectType(subjectTypeRepository.save(subjectType));
        operationalSubjectType.setUuid(UUID.randomUUID().toString());
        operationalSubjectTypeRepository.save(operationalSubjectType);
    }

    public SubjectType createWithDefaults(SubjectType subjectType, Form form) {
        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.setName(subjectType.getName());

        this.create(operationalSubjectType, subjectType);
        formRepository.save(form);
        formMappingRepository.save(new FormMappingBuilder().withForm(form).withSubjectType(subjectType).build());
        return subjectType;
    }

    public SubjectType createWithDefaults(SubjectType subjectType) {
        Form form = new TestFormBuilder().withDefaultFieldsForNewEntity().build();
        return this.createWithDefaults(subjectType, form);
    }
}
