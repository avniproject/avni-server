package org.avni.server.service.builder;

import org.avni.server.application.*;
import org.avni.server.dao.*;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class TestFormService {
    private final ConceptRepository conceptRepository;
    private final FormMappingRepository formMappingRepository;
    private final FormRepository formRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final OperationalProgramRepository operationalProgramRepository;
    private final OperationalEncounterTypeRepository operationalEncounterTypeRepository;

    public TestFormService(ConceptRepository conceptRepository, FormRepository formRepository, FormMappingRepository formMappingRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository, OperationalProgramRepository operationalProgramRepository, OperationalEncounterTypeRepository operationalEncounterTypeRepository) {
        this.conceptRepository = conceptRepository;
        this.formMappingRepository = formMappingRepository;
        this.formRepository = formRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.operationalProgramRepository = operationalProgramRepository;
        this.operationalEncounterTypeRepository = operationalEncounterTypeRepository;
    }

    public FormMapping createEnrolmentForm(SubjectType subjectType, Program program, String formName, List<String> singleSelectedConceptNames, List<String> multiSelectedConceptNames) {
        Form form = createForm(formName, singleSelectedConceptNames, multiSelectedConceptNames, FormType.ProgramEnrolment, null, null, null, null);
        FormMapping formMapping = new FormMappingBuilder().withSubjectType(subjectType).withProgram(program).withUuid(UUID.randomUUID().toString()).withForm(form).build();
        return formMappingRepository.save(formMapping);
    }

    public FormMapping createRegistrationForm(SubjectType subjectType, String formName, List<String> singleSelectedConceptNames, List<String> multiSelectedConceptNames, Concept questionGroupConcept, List<Concept> childQGConcepts, Concept repeatableQuestionGroupConcept, List<Concept> childRQGConcepts) {
        Form form = createForm(formName, singleSelectedConceptNames, multiSelectedConceptNames, FormType.IndividualProfile, questionGroupConcept, childQGConcepts, repeatableQuestionGroupConcept, childRQGConcepts);
        FormMapping formMapping = new FormMappingBuilder().withSubjectType(subjectType).withUuid(UUID.randomUUID().toString()).withForm(form).build();
        return formMappingRepository.save(formMapping);
    }

    private Form createForm(String formName, List<String> singleSelectedConceptNames, List<String> multiSelectedConceptNames, FormType formType, Concept questionGroupConcept, List<Concept> qgChildConcepts, Concept repeatableQuestionGroupConcept, List<Concept> childRQGConcepts) {
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
                    .withMandatory(true)
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

        if (questionGroupConcept != null) {
            FormElement qgFE = new TestFormElementBuilder()
                    .withUuid(UUID.randomUUID().toString())
                    .withName(questionGroupConcept.getName())
                    .withConcept(questionGroupConcept)
                    .withFormElementGroup(formElementGroup)
                    .withType(FormElementType.SingleSelect)
                    .withDisplayOrder(i++)
                    .build();

            for (Concept qgChildConcept : qgChildConcepts) {
                new TestFormElementBuilder()
                        .withUuid(UUID.randomUUID().toString())
                        .withName(qgChildConcept.getName())
                        .withConcept(qgChildConcept)
                        .withFormElementGroup(formElementGroup)
                        .withType(FormElementType.SingleSelect)
                        .withDisplayOrder(i++)
                        .withQuestionGroupElement(qgFE)
                        .withMandatory(qgChildConcept.getDataType().equals(ConceptDataType.Numeric.toString())) //arbitrarily setting numerics as mandatory to have a mix of mandatory and non mandatory form elements
                        .build();
            }
        }

        if (repeatableQuestionGroupConcept != null) {
            FormElement rqgFE = new TestFormElementBuilder()
                    .withUuid(UUID.randomUUID().toString())
                    .withName(repeatableQuestionGroupConcept.getName())
                    .withConcept(repeatableQuestionGroupConcept)
                    .withFormElementGroup(formElementGroup)
                    .withType(FormElementType.SingleSelect)
                    .withDisplayOrder(i++)
                    .withRepeatable(true)
                    .build();

            for (Concept rqgChildConcept : childRQGConcepts) {
                new TestFormElementBuilder()
                        .withUuid(UUID.randomUUID().toString())
                        .withName(rqgChildConcept.getName())
                        .withConcept(rqgChildConcept)
                        .withFormElementGroup(formElementGroup)
                        .withType(FormElementType.SingleSelect)
                        .withDisplayOrder(i++)
                        .withQuestionGroupElement(rqgFE)
                        .withMandatory(rqgChildConcept.getDataType().equals(ConceptDataType.Numeric.toString())) //arbitrarily setting numerics as mandatory to have a mix of mandatory and non mandatory form elements
                        .build();
            }
        }

        formRepository.save(form);
        return form;
    }

    public void addDecisionConcepts(Long formId, Concept... concepts) {
        Form form = formRepository.findEntity(formId);
        Arrays.stream(concepts).forEach(form::addDecisionConcept);
        formRepository.save(form);
    }

    // At the end of TestFormService.java

    private FormMapping createEncounterFormMapping(
            SubjectType subjectType,
            Program program,
            EncounterType encounterType,
            Form form
    ) {
        encounterTypeRepository.save(encounterType);
        OperationalEncounterType operationalEncounterType = new OperationalEncounterType();
        operationalEncounterType.setName(encounterType.getName());
        operationalEncounterType.setEncounterType(encounterType);
        operationalEncounterType.setUuid(UUID.randomUUID().toString());
        operationalEncounterTypeRepository.save(operationalEncounterType);

        FormMappingBuilder builder = new FormMappingBuilder()
                .withSubjectType(subjectType)
                .withEncounterType(encounterType)
                .withUuid(UUID.randomUUID().toString())
                .withForm(form);

        if (program != null) {
            builder.withProgram(program);
        }

        return formMappingRepository.save(builder.build());
    }

    public FormMapping createEncounterForm(
            SubjectType subjectType,
            EncounterType encounterType,
            String formName,
            List<String> singleSelectedConceptNames,
            List<String> multiSelectedConceptNames
    ) {
        Form form = createForm(formName, singleSelectedConceptNames, multiSelectedConceptNames, FormType.Encounter, null, null, null, null);
        return createEncounterFormMapping(subjectType, null, encounterType, form);
    }

    public FormMapping createProgramEncounterForm(
            SubjectType subjectType,
            Program program,
            EncounterType encounterType,
            String formName,
            List<String> singleSelectedConceptNames,
            List<String> multiSelectedConceptNames
    ) {
        Form form = createForm(formName, singleSelectedConceptNames, multiSelectedConceptNames, FormType.ProgramEncounter, null, null, null, null);
        return createEncounterFormMapping(subjectType, program, encounterType, form);
    }
}
