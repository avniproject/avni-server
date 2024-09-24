package org.avni.server.service;

import org.avni.server.application.*;
import org.avni.server.builder.FormBuilder;
import org.avni.server.builder.FormBuilderException;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.application.FormContract;
import org.avni.server.web.request.application.FormElementContract;
import org.avni.server.web.request.application.FormElementGroupContract;
import org.springframework.stereotype.Service;

import org.joda.time.DateTime;

import java.io.InvalidObjectException;
import java.util.*;
import java.util.stream.Collectors;

import static org.avni.server.application.FormElement.PLACEHOLDER_CONCEPT_NAME;
import static org.avni.server.application.FormElement.PLACEHOLDER_CONCEPT_UUID;
import static org.avni.server.domain.ConceptDataType.multiSelectTypes;

@Service
public class FormService implements NonScopeAwareService {
    private final FormRepository formRepository;
    private final OrganisationConfigService organisationConfigService;
    private final ConceptRepository conceptRepository;
    private final AccessControlService accessControlService;
    private final FormElementRepository formElementRepository;

    public FormService(FormRepository formRepository, OrganisationConfigService organisationConfigService, ConceptRepository conceptRepository, AccessControlService accessControlService, FormElementRepository formElementRepository) {
        this.formRepository = formRepository;
        this.organisationConfigService = organisationConfigService;
        this.conceptRepository = conceptRepository;
        this.accessControlService = accessControlService;
        this.formElementRepository = formElementRepository;
    }

    public void saveForm(FormContract formRequest) throws FormBuilderException {
        Form existingForm = formRepository.findByUuid(formRequest.getUuid());
        FormBuilder formBuilder = new FormBuilder(existingForm);
        Form form = formBuilder.withName(formRequest.getName())
                .withType(formRequest.getFormType())
                .withUUID(formRequest.getUuid())
                .withFormElementGroups(formRequest.getFormElementGroups())
                .withEditFormRule(formRequest.getEditFormRule())
                .withDecisionRule(formRequest.getDecisionRule())
                .withVisitScheduleRule(formRequest.getVisitScheduleRule())
                .withTaskScheduleRule(formRequest.getTaskScheduleRule())
                .withValidationRule(formRequest.getValidationRule())
                .withChecklistRule(formRequest.getChecklistsRule())
                .withVoided(formRequest.isVoided())
                .withValidationDeclarativeRule(formRequest.getValidationDeclarativeRule())
                .withDecisionDeclarativeRule(formRequest.getDecisionDeclarativeRule())
                .withVisitScheduleDeclarativeRule(formRequest.getVisitScheduleDeclarativeRule())
                .withTaskScheduleDeclarativeRule(formRequest.getTaskScheduleDeclarativeRule())
                .build();

        mapDecisionConcepts(formRequest, form);
        //Form audit values might not change for changes in form element groups or form elements.
        //This updateAudit forces audit updates
        form.updateAudit();
        accessControlService.checkPrivilege(FormType.getPrivilegeType(form));
        formRepository.save(form);
    }

    private void mapDecisionConcepts(FormContract formRequest, Form form) {
        formRequest.getDecisionConcepts().forEach(conceptContract -> {
            if (!form.hasDecisionConcept(conceptContract.getUuid())) {
                form.addDecisionConcept(conceptRepository.findByUuid(conceptContract.getUuid()));
            }
        });
        form.getDecisionConcepts().forEach(concept -> {
            if (formRequest.getDecisionConcepts().stream().filter(conceptContract -> conceptContract.getUuid().equals(concept.getUuid())).findFirst().orElse(null) == null) {
                form.removeDecisionConcept(concept);
            }
        });
    }

    public Form getOrCreateForm(String formUuid, String formName, FormType formType) {
        Form form = formRepository.findByUuid(formUuid);
        if (form != null) {
            return form;
        }

        form = Form.create();
        form.setName(formName);
        form.assignUUID();
        form.setFormType(formType);
        formRepository.save(form);
        return form;
    }

    public void checkIfLocationConceptsHaveBeenUsed(FormContract formRequest) {
        HashSet<String> locationConceptUuids = new HashSet<>();
        for (FormElementGroupContract formElementGroup : formRequest.getFormElementGroups()) {
            for (FormElementContract formElement : formElementGroup.getFormElements()) {
                if (formElement.getConcept().getDataType() != null && formElement.getConcept().getDataType().equals(String.valueOf(ConceptDataType.Location))) {
                    KeyValues keyValues = formElement.getConcept().getKeyValues();
                    if (keyValues != null && keyValues.containsKey(KeyType.lowestAddressLevelTypeUUIDs)) {
                        KeyValue isWithinCatchmentKeyValue = keyValues.get(KeyType.isWithinCatchment);
                        if (isWithinCatchmentKeyValue != null && !(boolean) isWithinCatchmentKeyValue.getValue()) {
                            locationConceptUuids.addAll((ArrayList<String>) keyValues.getKeyValue(KeyType.lowestAddressLevelTypeUUIDs).getValue());
                        }
                    }
                }
            }
        }
        if (!locationConceptUuids.isEmpty()) {
            organisationConfigService.updateLowestAddressLevelTypeSetting(locationConceptUuids);
        }
    }

    public List<FormElement> getFormElementNamesForLocationTypeForms() {
        List<Form> applicableForms = formRepository.findByFormTypeAndIsVoidedFalse(FormType.Location);

        return applicableForms.stream()
                .map(f -> {
                    List<FormElement> formElements = f.getAllFormElements();
                    formElements.addAll(createDecisionFormElement(f.getDecisionConcepts()));
                    return formElements;
                })
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .filter(formElement -> !formElement.isVoided())
                .sorted((a,b) -> (int) (a.getDisplayOrder() - b.getDisplayOrder()))
                .collect(Collectors.toList());
    }

    private List<FormElement> createDecisionFormElement(Set<Concept> concepts) {
        return concepts.stream().map(dc -> {
            FormElement formElement = new FormElement();
            formElement.setType(dc.getDataType().equals(ConceptDataType.Coded.name()) ? FormElementType.MultiSelect.name() : FormElementType.SingleSelect.name());
            formElement.setConcept(dc);
            return formElement;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return formRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public void validateForm(FormContract formContract) throws InvalidObjectException {
        HashSet<String> uniqueConcepts = new HashSet<>();

        for (FormElementGroupContract formElementGroup : formContract.getFormElementGroups()) {
            for (FormElementContract formElement : formElementGroup.getFormElements()) {
                String conceptUuid = formElement.getConcept().getUuid();
                String conceptName = formElement.getConcept().getName();
                if (!formElement.isVoided() && !formElement.isChildFormElement() &&
                        !PLACEHOLDER_CONCEPT_NAME.matcher(conceptName == null ? "" : conceptName).matches() &&
                        !conceptUuid.equals(PLACEHOLDER_CONCEPT_UUID) &&
                        !uniqueConcepts.add(conceptUuid)) {
                    throw new InvalidObjectException(String.format(
                            "Cannot use same concept twice. Form{uuid='%s',..} uses Concept{uuid='%s',..} twice",
                            formContract.getUuid(),
                            conceptUuid));
                }
                if (multiSelectTypes.contains(ConceptDataType.valueOf(formElement.getConcept().getDataType()))) {
                    FormElement existingFormElement = formElementRepository.findByUuid(formElement.getUuid());
                    if (existingFormElement != null) {
                        if (!existingFormElement.getType().equals(formElement.getType())) {
                            throw new InvalidObjectException("Cannot change from Single to Multi Select or vice versa");
                        }
                    }
                }
                if  (formElement.getConcept().isQuestionGroup()) {
                    FormElement existingFormElement = formElementRepository.findByUuid(formElement.getUuid());
                    if (existingFormElement != null) {
                        if (!existingFormElement.getKeyValues().get(KeyType.repeatable).equals(formElement.getKeyValues().get(KeyType.repeatable))) {
                            throw new InvalidObjectException("Cannot change from Repeatable to Non Repeatable or vice versa");
                        }
                    }
                }
            }
        }
    }
}
