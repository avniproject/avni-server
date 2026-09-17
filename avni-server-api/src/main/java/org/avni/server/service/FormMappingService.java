package org.avni.server.service;

import org.avni.server.application.*;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.dao.task.TaskTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.FormMappingContract;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.validation.ValidationException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FormMappingService implements NonScopeAwareService {
    private final ProgramRepository programRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final FormMappingRepository formMappingRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final FormRepository formRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final AccessControlService accessControlService;
    private static final Logger logger = LoggerFactory.getLogger(FormMappingService.class);

    @Autowired
    public FormMappingService(FormMappingRepository formMappingRepository,
                              EncounterTypeRepository encounterTypeRepository,
                              ProgramRepository programRepository,
                              SubjectTypeRepository subjectTypeRepository,
                              FormRepository formRepository,
                              TaskTypeRepository taskTypeRepository, AccessControlService accessControlService) {
        this.formMappingRepository = formMappingRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.programRepository = programRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.formRepository = formRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.accessControlService = accessControlService;
    }

    public void saveFormMapping(FormMappingParameterObject parametersForNewMapping,
                                Form form, boolean enableApproval) {
        FormMapping formMapping = formMappingRepository.getRequiredFormMapping(
                parametersForNewMapping.subjectTypeUuid,
                parametersForNewMapping.programUuid,
                parametersForNewMapping.encounterTypeUuid,
                form.getFormType());

        if (formMapping == null) {
            formMapping = new FormMapping();
            formMapping.assignUUID();
            formMapping.setVoided(false);
            formMapping.setEnableApproval(enableApproval);
        }

        setSubjectTypeIfRequired(formMapping, parametersForNewMapping.subjectTypeUuid);
        setProgramIfRequired(formMapping, form.getFormType(), parametersForNewMapping.programUuid);
        setEncounterTypeIfRequired(formMapping, form.getFormType(), parametersForNewMapping.encounterTypeUuid);
        formMapping.setForm(form);

        formMappingRepository.saveFormMapping(formMapping);
    }

    public void voidExistingFormMappings(FormMappingParameterObject mappingsToVoid, Form form) {
        FormType formType = form != null ? form.getFormType() : null;
        List<FormMapping> formMappingsToVoid = formMappingRepository.findRequiredFormMappings(
                mappingsToVoid.subjectTypeUuid,
                mappingsToVoid.programUuid,
                mappingsToVoid.encounterTypeUuid,
                formType
        );

        formMappingsToVoid.forEach(formMapping -> formMapping.setVoided(true));
        formMappingsToVoid.forEach(formMappingRepository::saveFormMapping);
    }

    public void createOrUpdateFormMapping(FormMappingContract formMappingRequest) {
        if (formMappingRequest.getFormUUID() == null) {
            throw new RuntimeException("FormMappingRequest without form uuid! " + formMappingRequest);
        }
        Form form = formRepository.findByUuid(formMappingRequest.getFormUUID());
        if (form == null) {
            throw new RuntimeException("Form not found!" + formMappingRequest);
        }
        FormMapping formMapping = formMappingRepository.findByUuid(formMappingRequest.getUuid());
        boolean isNewMapping = formMapping == null;
        if (isNewMapping) {
            formMapping = new FormMapping();
            formMapping.setUuid(formMappingRequest.getUuid());
        }
        // Taken before anything is set, so the duplicate check below can tell a real change from a no-op
        // re-save. FormSettings resubmits every one of a form's mappings on every save, and the database
        // constraint only fires on an actual insert or update - so checking unconditionally would refuse
        // saves the database allows.
        String combinationBeforeSave = isNewMapping ? null : combinationOf(formMapping);
        accessControlService.checkPrivilege(FormType.getPrivilegeType(form));
        formMapping.setForm(form);

        if (StringUtils.hasText(formMappingRequest.getSubjectTypeUUID())) {
            SubjectType subjectType = subjectTypeRepository.findByUuid(formMappingRequest.getSubjectTypeUUID());
            if (!formMappingRequest.isVoided() && form.getFormType().equals(FormType.IndividualProfile) && subjectType != null && subjectType.getType().equals(Subject.User)) {
                throw new ValidationException("Cannot associate Registration form with User subject type.");
            }
            formMapping.setSubjectType(subjectType);
        } else {
            formMapping.setSubjectType(subjectTypeRepository.individualSubjectType());
        }

        if (StringUtils.hasText(formMappingRequest.getProgramUUID())) {
            formMapping.setProgram(programRepository.findByUuid(formMappingRequest.getProgramUUID()));
        }

        if (StringUtils.hasText(formMappingRequest.getEncounterTypeUUID())) {
            formMapping.setEncounterType(encounterTypeRepository.findByUuid(formMappingRequest.getEncounterTypeUUID()));
        }

        if (StringUtils.hasText(formMappingRequest.getTaskTypeUUID())) {
            formMapping.setTaskType(taskTypeRepository.findByUuid(formMappingRequest.getTaskTypeUUID()));
        }

        formMapping.setVoided(formMappingRequest.isVoided());
        formMapping.setEnableApproval(formMappingRequest.getEnableApproval());

        if (!formMappingRequest.isVoided() && form.getFormType().isApprovalDecisionForm()) {
            assertCombinationCanProduceAnApproval(formMapping);
        }

        // Only when this row is new or its combination actually moved. Two live mappings can already share
        // a combination - changing a form's type does not re-run the constraint on form_mapping - and
        // checking every resubmitted row would make both of those forms permanently unsaveable, with no
        // way out through App Designer, because the clash is with a mapping belonging to another form.
        if (isNewMapping || !combinationOf(formMapping).equals(combinationBeforeSave)) {
            assertNoDuplicateMapping(formMapping);
        }

        formMappingRepository.saveFormMapping(formMapping);
    }

    /**
     * The same clash the check_form_mapping_uniqueness constraint catches, reported before it fires.
     *
     * The constraint raises a bare plpgsql error, so Postgres emits SQLSTATE P0001 rather than a check
     * violation. That arrives as a JpaSystemException, misses the handler for constraint violations, and
     * falls through to the catch-all - which returns 500 with the full stack trace in the body and files a
     * Bugsnag incident. What the administrator reads is "Duplicate form mapping exists for:
     * organisation_id: 586, subject_type_id: 3194 ...", which names nothing they can act on.
     *
     * Raising it here instead produces a 400 naming the form already holding that combination. The
     * constraint stays as the backstop for every other write path.
     *
     * The client cannot do this check: FormSettings compares a form's mappings against each other, and the
     * clash that produces this error is with a mapping belonging to a different form.
     */
    private void assertNoDuplicateMapping(FormMapping formMapping) {
        if (formMapping.isVoided()) return;

        List<FormMapping> duplicates = formMappingRepository.findDuplicateFormMappings(
                UserContextHolder.getUserContext().getOrganisationId(),
                idOf(formMapping.getSubjectType()),
                idOf(formMapping.getProgram()),
                idOf(formMapping.getEncounterType()),
                idOf(formMapping.getTaskType()),
                formMapping.getForm().getFormType(),
                formMapping.getId());

        if (duplicates.isEmpty()) return;

        throw new ValidationException(String.format(
                "%s is already attached to the %s, and a combination can hold only one form of a type.",
                duplicates.get(0).getForm().getName(), describeCombination(formMapping)));
    }

    /**
     * An Approval or Rejection form is only ever shown at the moment an approval decision is made, so
     * attaching one where no approval can happen builds a form nobody will ever see. Requires a sibling
     * mapping on the same combination that has approval switched on AND whose form type actually creates
     * an EntityApprovalStatus row.
     * <p>
     * Checking enable_approval alone is not enough: ManualProgramEnrolmentEligibility carries the flag in
     * two production organisations but produces no approval, which is the specific case #1052 exists to
     * stop. 108 production triples carry two approval-enabled form types, so this looks across a list
     * rather than assuming a single sibling.
     * <p>
     * The tuple is built from the resolved entities, not the request: createOrUpdateFormMapping defaults a
     * missing subject type to the Individual subject type, so reading subjectTypeUUID off the request
     * would miss every subject-only mapping. Organisation is not part of the Java-side key - row level
     * security scopes the query to the caller's organisation.
     * <p>
     * The mapping being saved can never satisfy its own condition, because Approval and Rejection have no
     * EntityApprovalStatus.EntityType. No self-exclusion clause is needed.
     */
    private void assertCombinationCanProduceAnApproval(FormMapping formMapping) {
        List<FormMapping> approvalEnabledSiblings = formMappingRepository.findApprovalEnabledMappingsForCombination(
                idOf(formMapping.getSubjectType()), idOf(formMapping.getProgram()), idOf(formMapping.getEncounterType()));
        // form_id is nullable (V1_146) and createOrUpdateEmptyFormMapping sets it to null explicitly, so a
        // form-less approval-enabled row reaches here. Skip it rather than dereferencing it - otherwise an
        // organisation holding one gets a 500 where it should get the validation message below.
        boolean anySiblingProducesAnApproval = approvalEnabledSiblings.stream()
                .anyMatch(sibling -> sibling.getForm() != null
                        && sibling.getForm().getFormType().getApprovalEntityType() != null);
        if (!anySiblingProducesAnApproval) {
            String combination = describeCombination(formMapping);
            String article = articleFor(formMapping.getForm().getFormType());
            if (approvalEnabledSiblings.isEmpty()) {
                throw new ValidationException(String.format(
                        "Approval is not switched on for the %s, therefore %s form cannot be attached.",
                        combination, article));
            }
            throw new ValidationException(String.format(
                    "Approval is switched on only for the %s, which never produces an approval to decide on, " +
                            "therefore %s form cannot be attached. Switch it on for the %s instead.",
                    namesOf(approvalEnabledSiblings), article, combination));
        }
    }

    /**
     * Everything check_form_mapping_uniqueness keys on, plus the voided flag. Comparing this before and
     * after tells a genuine change from a resubmission of an unchanged row, which is the difference
     * between the constraint firing and staying silent.
     */
    private String combinationOf(FormMapping formMapping) {
        return String.join("|",
                String.valueOf(idOf(formMapping.getForm())),
                String.valueOf(idOf(formMapping.getSubjectType())),
                String.valueOf(idOf(formMapping.getProgram())),
                String.valueOf(idOf(formMapping.getEncounterType())),
                String.valueOf(idOf(formMapping.getTaskType())),
                String.valueOf(formMapping.isVoided()));
    }

    private Long idOf(CHSEntity entity) {
        return entity == null ? null : entity.getId();
    }

    /**
     * The message is shown to a non-developer administrator in App Designer, so it has to read as English:
     * "an Approval form", not "a Approval form".
     */
    private String articleFor(FormType formType) {
        return (formType == FormType.Approval ? "an " : "a ") + formType;
    }

    /**
     * Names the one form that has to carry the approval switch, rather than listing every form type it
     * could be. The reader is an administrator who has just chosen a subject type, programme and visit
     * type, so "the Mother registration form" sends them somewhere; naming the combination in the
     * abstract left them to work out which of six forms applied to what they had picked.
     * <p>
     * Both the enrolment and the exit form of a programme produce an approval, as do both the visit and
     * the visit cancellation form of a visit type, so each pair is named rather than guessed between. The
     * subject type is carried into the programme and visit wording because one programme can be attached
     * to several subject types.
     */
    private String describeCombination(FormMapping formMapping) {
        String subjectTypeName = formMapping.getSubjectType().getName();
        EncounterType encounterType = formMapping.getEncounterType();
        Program program = formMapping.getProgram();
        if (encounterType != null) {
            return program == null
                    ? String.format("%s visit or visit cancellation form for %s", encounterType.getName(), subjectTypeName)
                    : String.format("%s visit or visit cancellation form for %s in %s", encounterType.getName(), subjectTypeName, program.getName());
        }
        if (program != null) {
            return String.format("%s enrolment or exit form for %s", program.getName(), subjectTypeName);
        }
        return String.format("%s registration form", subjectTypeName);
    }

    /**
     * The forms that do carry the switch but cannot produce an approval. Named so the administrator can
     * see that the switch they already set is on the wrong form, rather than being told approval is off
     * when they know they turned it on.
     */
    private String namesOf(List<FormMapping> formMappings) {
        return formMappings.stream()
                .map(FormMapping::getForm)
                .filter(Objects::nonNull)
                .map(Form::getName)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    public void createOrUpdateEmptyFormMapping(FormMappingContract formMappingRequest) {
        FormMapping formMapping = formMappingRepository.findByUuid(formMappingRequest.getUuid());
        if (formMapping == null) {
            formMapping = new FormMapping();
            formMapping.setUuid(formMappingRequest.getUuid());
        }

        Form form = null;
        if (formMappingRequest.getFormUUID() != null) {
            form = formRepository.findByUuid(formMappingRequest.getFormUUID());
        }
        formMapping.setForm(form);

        if (formMappingRequest.getProgramUUID() != null) {
            formMapping.setProgram(programRepository.findByUuid(formMappingRequest.getProgramUUID()));
        } else {
            formMapping.setProgram(null);
        }

        if (formMappingRequest.getEncounterTypeUUID() != null) {
            formMapping.setEncounterType(encounterTypeRepository.findByUuid(formMappingRequest.getEncounterTypeUUID()));
        } else {
            formMapping.setEncounterType(null);
        }

        if (formMappingRequest.getSubjectTypeUUID() != null) {
            formMapping.setSubjectType(
                    subjectTypeRepository.findByUuid(
                            formMappingRequest.getSubjectTypeUUID()));
        } else {
            formMapping.setSubjectType(null);
        }

        // getIsVoided() reads a field only Jackson populates - FormMappingContract has no setter for it, and
        // fromFormMapping sets the superclass's flag instead. So any contract not deserialised from JSON
        // arrives with it null and unboxing it here threw. Default to not-voided rather than blowing up.
        formMapping.setVoided(Boolean.TRUE.equals(formMappingRequest.getIsVoided()));
        formMapping.setEnableApproval(formMappingRequest.getEnableApproval());

        // The same guard createOrUpdateFormMapping applies. Without it this route could create exactly the
        // orphaned decision form #1052 exists to refuse. A form-less mapping carries no form type, so there
        // is nothing to check and refusing it would break what this route is for.
        // Read the flag off the request, not off the entity: setVoided above accepts a null Boolean, and
        // unboxing that here would turn a missing isVoided into an NPE on a route that tolerates it today.
        if (!Boolean.TRUE.equals(formMappingRequest.getIsVoided())
                && form != null && form.getFormType().isApprovalDecisionForm()) {
            assertCombinationCanProduceAnApproval(formMapping);
        }

        formMappingRepository.saveFormMapping(formMapping);
    }

    private void setEncounterTypeIfRequired(FormMapping formMapping, FormType formType, String encounterTypeUuid) {
        if (formType.isLinkedToEncounterType() && encounterTypeUuid != null) {
            EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUuid);
            if (encounterType == null) throw new BadRequestError("Encounter Type %s not found", encounterTypeUuid);
            formMapping.setEncounterType(encounterType);
        }
    }

    private void setProgramIfRequired(FormMapping formMapping, FormType formType, String programUuid) {
        if (formType.isLinkedToProgram()) {
            Program program = programRepository.findByUuid(programUuid);
            formMapping.setProgram(program);
        }
    }

    private void setSubjectTypeIfRequired(FormMapping formMapping, String requestSubjectType) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(requestSubjectType);
        if (subjectType == null) throw new BadRequestError("Subject type %s not found", requestSubjectType);
        formMapping.setSubjectType(subjectType);
    }

    public LinkedHashMap<String, FormElement> getAllFormElementsAndDecisionMap(String subjectTypeUUID, String programUUID, String encounterTypeUUID, FormType formType) {
        return getEntityConceptMap(formMappingRepository.getRequiredFormMapping(subjectTypeUUID, programUUID, encounterTypeUUID, formType), false);
    }

    public LinkedHashMap<String, FormElement> getEntityConceptMap(FormMapping formMapping, boolean includeVoidedFormElements) {
        List<FormElement> formElements = formMapping == null ? new ArrayList<>() : includeVoidedFormElements ? formMapping.getForm().getAllFormElements() : formMapping.getForm().getApplicableFormElements();
        formElements.addAll(getDecisionFormElements(formMapping));
        return formElements.stream().collect(Collectors.toMap(f -> f.getConcept().getUuid(), f -> f, (a, b) -> b, LinkedHashMap::new));
    }

    public LinkedHashMap<String, FormElement> getEntityConceptMapForSpecificQuestionGroupFormElement(FormElement questionGroupFormElement,
                                                                                                     FormMapping formMapping, boolean includeVoidedFormElements) {
        List<FormElement> formElements = new ArrayList<>();
        if (questionGroupFormElement != null && StringUtils.hasText(questionGroupFormElement.getUuid())) {
            if (formMapping != null) {
                formElements = includeVoidedFormElements ? formMapping.getForm().getAllFormElements()
                        : formMapping.getForm().getApplicableFormElements();
            }
            formElements = formElements.stream().filter(fe -> fe.isPartOfQuestionGroup() && fe.getGroup().getUuid().equals(questionGroupFormElement.getUuid())).collect(Collectors.toList());
        }
        return formElements.stream().collect(Collectors.toMap(f -> f.getConcept().getUuid(), f -> f, (a, b) -> b, LinkedHashMap::new));
    }

    private List<FormElement> getDecisionFormElements(FormMapping formMapping) {
        Set<Concept> decisionConcepts = formMapping == null ? Collections.emptySet() : formMapping.getForm().getDecisionConcepts();
        return decisionConcepts.stream().map(concept -> {
            FormElement formElement = new FormElement();
            formElement.setType(concept.getDataType().equals(ConceptDataType.Coded.name()) ? FormElementType.MultiSelect.name() : FormElementType.SingleSelect.name());
            formElement.setConcept(concept);
            return formElement;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return formMappingRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public FormMapping find(EncounterType encounterType, FormType formType) {
        FormMapping formMapping = formMappingRepository.findByFormFormTypeAndIsVoidedFalse(formType)
                .stream()
                .filter(fm -> encounterType.equals(fm.getEncounterType()))
                .findFirst()
                .orElse(null);
        if (formMapping == null) {
            formMapping = formMappingRepository.findByFormFormTypeAndIsVoidedTrueOrderByLastModifiedDateTimeDesc(formType)
                    .stream()
                    .filter(fm -> encounterType.equals(fm.getEncounterType()))
                    .findFirst()
                    .orElse(null);
        }
        return formMapping;
    }

    public FormMapping find(Program program, FormType formType) {
        FormMapping formMapping = formMappingRepository.findByFormFormTypeAndIsVoidedFalse(formType)
                .stream()
                .filter(fm -> program.equals(fm.getProgram()))
                .findFirst()
                .orElse(null);
        if (formMapping == null) {
            formMapping = formMappingRepository.findByFormFormTypeAndIsVoidedTrueOrderByLastModifiedDateTimeDesc(formType)
                    .stream()
                    .filter(fm -> program.equals(fm.getProgram()))
                    .findFirst()
                    .orElse(null);
        }
        return formMapping;
    }

    public FormMapping find(SubjectType subjectType) {
        return formMappingRepository.getRegistrationFormMapping(subjectType);
    }

    public FormMapping findForSubject(String subjectTypeUUID) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        return this.find(subjectType);
    }

    public FormMapping findForEncounter(String encounterUuid, FormType formType) {
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterUuid);
        return this.find(encounterType, formType);
    }

    public FormMapping findForProgram(String programUuid, FormType formType) {
        Program program = programRepository.findByUuid(programUuid);
        return this.find(program, formType);
    }

    public FormMapping findBy(SubjectType subjectType, Program program, EncounterType encounterType, FormType formType) {
        return formMappingRepository.findBySubjectTypeAndProgramAndEncounterTypeAndIsVoidedFalseAndFormFormType(subjectType, program, encounterType, formType);
    }

    public List<Program> getAllPrograms(List<String> subjectTypeUuids) {
        List<SubjectType> subjectTypes = subjectTypeRepository.findAllByUuidIn(subjectTypeUuids);
        return formMappingRepository.getAllProgramEnrolmentFormMapping(subjectTypes).stream().filter(formMapping -> formMapping.getForm().getFormType().equals(FormType.ProgramEnrolment)).map(FormMapping::getProgram).filter(program -> !program.isVoided()).collect(Collectors.toList());
    }

    private List<EncounterType> getUniqueEncounterTypes(List<FormMapping> formMappings, FormType formType) {
        return formMappings.stream()
                .filter(x -> x.getForm().getFormType().equals(formType))
                .map(FormMapping::getEncounterType)
                .filter(encounterType -> !encounterType.isVoided())
                .collect(Collectors.toList());
    }

    public List<EncounterType> getEncounterTypes(List<String> subjectTypeUuids) {
        List<SubjectType> subjectTypes = subjectTypeRepository.findAllByUuidIn(subjectTypeUuids);
        return getUniqueEncounterTypes(formMappingRepository.getAllGeneralEncounterTypeFormMapping(subjectTypes), FormType.Encounter);
    }

    public List<EncounterType> getEncounterTypes(List<String> subjectTypeUuids, List<String> programUuids) {
        List<SubjectType> subjectTypes = subjectTypeRepository.findAllByUuidIn(subjectTypeUuids);
        List<Program> programs = programRepository.findAllByUuidIn(programUuids);
        return getUniqueEncounterTypes(formMappingRepository.getAllProgramEncounterTypeFormMapping(subjectTypes, programs), FormType.ProgramEncounter);
    }
}
