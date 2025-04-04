package org.avni.server.service.builder;

import java.util.UUID;
import java.util.List;

import org.avni.server.application.*;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.springframework.stereotype.Component;

@Component
public class TestFormService {
    private final ConceptRepository conceptRepository;
    private final FormMappingRepository formMappingRepository;
    private final FormRepository formRepository;

    public TestFormService(ConceptRepository conceptRepository, FormRepository formRepository, FormMappingRepository formMappingRepository) {
        this.conceptRepository = conceptRepository;
        this.formMappingRepository = formMappingRepository;
        this.formRepository = formRepository;
    }

    public FormMapping createRegistrationForm(SubjectType subjectType, String formName, FormType formType, List<String> singleSelectedConceptNames, List<String> multiSelectedConceptNames) {
        Form form = new TestFormBuilder().withName(formName).withFormType(formType).withUuid(UUID.randomUUID().toString()).build();
        form = formRepository.save(form);

        FormElementGroup formElementGroup = new TestFormElementGroupBuilder().withName("Default").withUuid(UUID.randomUUID().toString()).withDisplayOrder(1d).build();
        formElementGroup.setForm(form);
        form.addFormElementGroup(formElementGroup);

        double i = 1;
        for (String conceptName : singleSelectedConceptNames) {
            Concept concept = conceptRepository.findByName(conceptName);
            new TestFormElementBuilder()
                    .withUuid(UUID.randomUUID().toString())
                    .withName(conceptName)
                    .withConcept(concept)
                    .withType(FormElementType.SingleSelect)
                    .withDisplayOrder(i++)
                    .withFormElementGroup(formElementGroup)
                    .build();
        }
        for (String conceptName : multiSelectedConceptNames) {
            Concept concept = conceptRepository.findByName(conceptName);
            new TestFormElementBuilder()
                    .withUuid(UUID.randomUUID().toString())
                    .withName(conceptName)
                    .withConcept(concept)
                    .withType(FormElementType.MultiSelect)
                    .withDisplayOrder(i++)
                    .withFormElementGroup(formElementGroup)
                    .build();
        }
        formRepository.save(form);

        FormMapping formMapping = new FormMappingBuilder().withSubjectType(subjectType).withUuid(UUID.randomUUID().toString()).withForm(form).build();
        return formMappingRepository.save(formMapping);
    }
}
