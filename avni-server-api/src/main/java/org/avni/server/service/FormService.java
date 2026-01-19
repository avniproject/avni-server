package org.avni.server.service;

import org.avni.server.application.*;
import org.avni.server.builder.FormBuilder;
import org.avni.server.builder.FormBuilderException;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.application.FormElementGroupRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.application.FormContract;
import org.avni.server.web.request.application.FormElementContract;
import org.avni.server.web.request.application.FormElementGroupContract;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InvalidObjectException;
import java.util.*;
import java.util.stream.Collectors;

import static org.avni.server.application.FormElement.PLACEHOLDER_CONCEPT_NAME;
import static org.avni.server.application.FormElement.PLACEHOLDER_CONCEPT_UUID;
import static org.avni.server.domain.ConceptDataType.multiSelectTypes;
import static org.springframework.util.ObjectUtils.nullSafeEquals;

@Service
public class FormService implements NonScopeAwareService {
    private static final Logger logger = LoggerFactory.getLogger(FormService.class);
    
    private final FormRepository formRepository;
    private final OrganisationConfigService organisationConfigService;
    private final ConceptRepository conceptRepository;
    private final AccessControlService accessControlService;
    private final FormElementRepository formElementRepository;
    private final FormElementGroupRepository formElementGroupRepository;

    public FormService(FormRepository formRepository, OrganisationConfigService organisationConfigService, ConceptRepository conceptRepository, AccessControlService accessControlService, FormElementRepository formElementRepository, FormElementGroupRepository formElementGroupRepository) {
        this.formRepository = formRepository;
        this.organisationConfigService = organisationConfigService;
        this.conceptRepository = conceptRepository;
        this.accessControlService = accessControlService;
        this.formElementRepository = formElementRepository;
        this.formElementGroupRepository = formElementGroupRepository;
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
        validateDisplayOrderConstraints(formContract);
        
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
                            "Cannot use same concept twice. Form{uuid='%s',..} uses Concept{name='%s',..} twice",
                            formContract.getUuid(),
                            conceptName));
                }
                if (!formElement.isVoided() && formElement.isChildFormElement() &&
                        !uniqueConcepts.add(formElement.getParentFormElementUuid()+"#"+conceptUuid)) {
                    throw new InvalidObjectException(String.format(
                            "Cannot use same concept twice. Form{uuid='%s',..} , QuestionGroup{name='%s',..},uses Concept{name='%s',..} twice",
                            formContract.getUuid(),
                            formElement.getName(),
                            conceptName));
                }
                String conceptDataType = formElement.getConcept().getDataType();
                if (conceptDataType != null && multiSelectTypes.contains(ConceptDataType.valueOf(conceptDataType))) {
                    FormElement existingFormElement = formElementRepository.findByUuid(formElement.getUuid());
                    if (existingFormElement != null) {
                        if (!existingFormElement.getType().equals(formElement.getType())) {
                            throw new InvalidObjectException(String.format("Cannot change from Single to Multi Select or vice versa for form element: %s", existingFormElement.getName()));
                        }
                    }
                }
                if  (formElement.getConcept().isQuestionGroup()) {
                    FormElement existingFormElement = formElementRepository.findByUuid(formElement.getUuid());
                    if (existingFormElement != null) {
                        KeyValue existingKeyValue = existingFormElement.getKeyValues().get(KeyType.repeatable);
                        KeyValue newKeyValue = formElement.getKeyValues().get(KeyType.repeatable);
                        Boolean existingRepeatable = existingKeyValue != null ? (Boolean) existingKeyValue.getValue() : null;
                        Boolean newRepeatable = newKeyValue != null ? (Boolean) newKeyValue.getValue() : null;
                        if (!((existingRepeatable == null && (newRepeatable == null || newRepeatable.equals(Boolean.FALSE))) || nullSafeEquals(existingRepeatable, newRepeatable))) {
                            throw new InvalidObjectException(String.format("Cannot change from Repeatable to Non Repeatable or vice versa for form element: %s", existingFormElement.getName()));
                        }
                    }
                }
            }
        }
    }
    
    private void validateDisplayOrderConstraints(FormContract formContract) {
        validateFormElementGroupDisplayOrders(formContract);
        validateFormElementDisplayOrders(formContract);
        validateAgainstExistingData(formContract);
    }
    
    private List<String> createErrorList() {
        return new ArrayList<>();
    }
    
    private void throwValidationErrorsIfAny(List<String> errorList) {
        if (!errorList.isEmpty()) {
            String combinedErrors = String.join("\n", errorList);
            throw new RuntimeException(combinedErrors);
        }
    }
    
    private void validateFormElementGroupDisplayOrders(FormContract formContract) {
        Map<Double, List<FormElementGroupContract>> groupDisplayOrderMap = formContract.getFormElementGroups().stream()
            .filter(group -> !group.isVoided())
            .collect(Collectors.groupingBy(FormElementGroupContract::getDisplayOrder));
        List<String> errorList = createErrorList();
        for (Map.Entry<Double, List<FormElementGroupContract>> entry : groupDisplayOrderMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String errorMsg = String.format("Duplicate displayOrder %.1f found in form element groups: %s", 
                    entry.getKey(), 
                    entry.getValue().stream().map(g -> g.getName() + " (" + g.getUuid() + ")").collect(Collectors.joining(", ")));
                logger.error("DisplayOrder validation failed: {}", errorMsg);
                errorList.add(errorMsg);
            }
        }
        throwValidationErrorsIfAny(errorList);
    }
    
    private void validateFormElementDisplayOrders(FormContract formContract) {
        for (FormElementGroupContract group : formContract.getFormElementGroups()) {
            if (group.getFormElements() != null) {
                Map<Double, List<FormElementContract>> elementDisplayOrderMap = group.getFormElements().stream()
                    .filter(element -> !element.isVoided())
                    .collect(Collectors.groupingBy(FormElementContract::getDisplayOrder));
                List<String> errorList = createErrorList(); 
                for (Map.Entry<Double, List<FormElementContract>> entry : elementDisplayOrderMap.entrySet()) {
                    if (entry.getValue().size() > 1) {
                        String errorMsg = String.format("Duplicate displayOrder %.1f found in form elements of group '%s': %s", 
                            entry.getKey(),
                            group.getName(),
                            entry.getValue().stream().map(e -> e.getName() + " (" + e.getUuid() + ")").collect(Collectors.joining(", ")));
                        logger.error("DisplayOrder validation failed: {}", errorMsg);
                        errorList.add(errorMsg);
                    }
                }
                throwValidationErrorsIfAny(errorList);
            }
        }
    }
    
    private void validateAgainstExistingData(FormContract formContract) {
        Form existingForm = formRepository.findByUuid(formContract.getUuid());
        if (existingForm != null) {
            Long currentOrganisationId = getCurrentOrganisationId();
            List<FormElementGroup> existingGroups = getExistingFormElementGroups(existingForm, currentOrganisationId);
            
            validateGroupConflicts(formContract, existingGroups, currentOrganisationId);
            validateElementConflicts(formContract, existingGroups, currentOrganisationId);
        }
    }
    
    private Long getCurrentOrganisationId() {
        return UserContextHolder.getUserContext().getOrganisation().getId();
    }
    
    private List<FormElementGroup> getExistingFormElementGroups(Form existingForm, Long organisationId) {
        return formElementGroupRepository.findAll().stream()
            .filter(feg -> 
                feg.getForm().getId().equals(existingForm.getId()) && 
                !feg.isVoided() &&
                feg.getOrganisationId().equals(organisationId))
            .collect(Collectors.toList());
    }
    
    private void validateGroupConflicts(FormContract formContract, List<FormElementGroup> existingGroups, Long organisationId) {
        List<FormElementGroupContract> incomingGroups = formContract.getFormElementGroups();
        
        // Keep nonVoided existing groups and map displayOrder for existing group UUIDs
        Map<String, Double> uuidToDisplayOrderMap = existingGroups.stream()
            .filter(group -> !group.isVoided())
            .collect(Collectors.toMap(
                FormElementGroup::getUuid,
                FormElementGroup::getDisplayOrder
            ));
        
        // Process incoming groups
        for (FormElementGroupContract incomingGroup : incomingGroups) {
            if (incomingGroup.isVoided()) {
                // Remove voided incoming groups from mapping
                uuidToDisplayOrderMap.remove(incomingGroup.getUuid());
            } else {
                // update incoming group displayOrder in mapping
                uuidToDisplayOrderMap.put(incomingGroup.getUuid(), incomingGroup.getDisplayOrder());
            }
        }

        //check for duplicate displayOrder across all uuids
        Map<Double, List<String>> displayOrderToUuidsMap = uuidToDisplayOrderMap.entrySet().stream()
            .collect(Collectors.groupingBy(
                Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())
            ));
        
        List<String> errorList = createErrorList();
        
        for (Map.Entry<Double, List<String>> entry : displayOrderToUuidsMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String errorMsg = String.format(
                    "DisplayOrder %.1f is used by multiple form element groups in organisation %d: UUIDs %s", 
                    entry.getKey(), organisationId, String.join(", ", entry.getValue()));
                logger.error("DisplayOrder validation failed: {}", errorMsg);
                errorList.add(errorMsg);
            }
        }

        throwValidationErrorsIfAny(errorList);
    }
    
    private void validateElementConflicts(FormContract formContract, List<FormElementGroup> existingGroups, Long organisationId) {
        for (FormElementGroupContract incomingGroup : formContract.getFormElementGroups()) {
            if (!incomingGroup.isVoided()) {
                FormElementGroup matchingExistingGroup = findMatchingExistingGroup(existingGroups, incomingGroup);
                
                if (matchingExistingGroup != null && incomingGroup.getFormElements() != null) {
                    List<FormElement> existingElements = getExistingFormElements(matchingExistingGroup, organisationId);
                    checkElementConflicts(incomingGroup, existingElements, organisationId);
                }
            }
        }
    }
    
    private FormElementGroup findMatchingExistingGroup(List<FormElementGroup> existingGroups, FormElementGroupContract incomingGroup) {
        return existingGroups.stream()
            .filter(g -> g.getUuid().equals(incomingGroup.getUuid()))
            .findFirst()
            .orElse(null);
    }
    
    private List<FormElement> getExistingFormElements(FormElementGroup group, Long organisationId) {
        return formElementRepository.findAll().stream()
            .filter(fe -> 
                fe.getFormElementGroup().getId().equals(group.getId()) && 
                !fe.isVoided() &&
                fe.getOrganisationId().equals(organisationId))
            .collect(Collectors.toList());
    }
    
    private void checkElementConflicts(FormElementGroupContract incomingGroup, List<FormElement> existingElements, Long organisationId) {
        List<FormElementContract> incomingElements = incomingGroup.getFormElements();
        
        Map<String, Double> uuidToDisplayOrderMap = existingElements.stream()
            .filter(element -> !element.isVoided())
            .collect(Collectors.toMap(
                FormElement::getUuid,
                FormElement::getDisplayOrder
            ));
        
        // Process incoming elements
        for (FormElementContract incomingElement : incomingElements) {
            if (incomingElement.isVoided()) {
                // Remove voided incoming elements from mapping
                uuidToDisplayOrderMap.remove(incomingElement.getUuid());
            } else {
                // update incoming element displayOrder in mapping
                uuidToDisplayOrderMap.put(incomingElement.getUuid(), incomingElement.getDisplayOrder());
            }
        }

        //check for duplicate displayOrder across all uuids
        Map<Double, List<String>> displayOrderToUuidsMap = uuidToDisplayOrderMap.entrySet().stream()
            .collect(Collectors.groupingBy(
                Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())
            ));
        
        List<String> errorList = createErrorList();
        for (Map.Entry<Double, List<String>> entry : displayOrderToUuidsMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String errorMsg = String.format(
                    "Form element displayOrder %.1f is used by multiple elements in group (organisation %d): UUIDs %s", 
                    entry.getKey(), organisationId, String.join(", ", entry.getValue()));
                logger.error("DisplayOrder validation failed: {}", errorMsg);
                errorList.add(errorMsg);
            }
        }

        throwValidationErrorsIfAny(errorList);
    }
}
