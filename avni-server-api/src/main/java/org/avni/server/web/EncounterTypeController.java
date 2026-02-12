package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.OperationalEncounterTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Program;
import org.avni.server.domain.OperationalEncounterType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.service.EncounterTypeService;
import org.avni.server.service.FormMappingParameterObject;
import org.avni.server.service.FormMappingService;
import org.avni.server.service.FormService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.request.EntityTypeContract;
import org.avni.server.web.request.webapp.EncounterTypeContractWeb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class EncounterTypeController extends AbstractController<EncounterType> implements RestControllerResourceProcessor<EncounterTypeContractWeb> {
    private final Logger logger;
    private final OperationalEncounterTypeRepository operationalEncounterTypeRepository;
    private final EncounterTypeService encounterTypeService;
    private final EncounterTypeRepository encounterTypeRepository;
    private final FormService formService;
    private final FormMappingService formMappingService;
    private final FormRepository formRepository;
    private final FormMappingRepository formMappingRepository;
    private final ProgramRepository programRepository;
    private final AccessControlService accessControlService;

    @Autowired
    public EncounterTypeController(EncounterTypeRepository encounterTypeRepository,
                                   OperationalEncounterTypeRepository operationalEncounterTypeRepository,
                                   EncounterTypeService encounterTypeService,
                                   FormService formService,
                                   FormMappingService formMappingSevice,
                                   FormRepository formRepository,
                                   FormMappingRepository formMappingRepository,
                                   ProgramRepository programRepository,
                                   AccessControlService accessControlService) {
        this.encounterTypeRepository = encounterTypeRepository;
        this.operationalEncounterTypeRepository = operationalEncounterTypeRepository;
        this.encounterTypeService = encounterTypeService;
        this.formService = formService;
        this.formMappingService = formMappingSevice;
        this.formRepository = formRepository;
        this.formMappingRepository = formMappingRepository;
        this.programRepository = programRepository;
        this.accessControlService = accessControlService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/encounterTypes", method = RequestMethod.POST)
    @Transactional
    void save(@RequestBody List<EntityTypeContract> encounterTypeRequests) {
        accessControlService.checkPrivilege(PrivilegeType.EditEncounterType);
        for (EntityTypeContract encounterTypeRequest : encounterTypeRequests) {
            encounterTypeService.createEncounterType(encounterTypeRequest);
        }
    }

    @GetMapping(value = "/web/encounterType")
    @ResponseBody
    public CollectionModel<EntityModel<EncounterTypeContractWeb>> getAll(Pageable pageable) {
        return wrap(operationalEncounterTypeRepository
                .findPageByIsVoidedFalse(pageable)
                .map(EncounterTypeContractWeb::fromOperationalEncounterType));
    }

    @GetMapping(value = "/web/encounterType/v2")
    public List<EncounterTypeContract> getAllEncounterTypes(@RequestParam(required = false, name = "subjectType") List<String> subjectTypeUuids,
                                                      @RequestParam(required = false, name = "program") List<String> programUuids) {
        List<EncounterType> allEncounterTypes;
        if (CollectionUtils.isEmpty(subjectTypeUuids) && CollectionUtils.isEmpty(programUuids)) {
            allEncounterTypes = encounterTypeRepository.findAllByIsVoidedFalseOrderByName();
        } else if (CollectionUtils.isEmpty(programUuids)) {
            allEncounterTypes = formMappingService.getEncounterTypes(subjectTypeUuids);
        } else {
            allEncounterTypes = formMappingService.getEncounterTypes(subjectTypeUuids, programUuids);
        }

        return allEncounterTypes.stream().map(encounterType -> {
            EncounterTypeContract contract = new EncounterTypeContract();
            contract.setName(encounterType.getName());
            contract.setUuid(encounterType.getUuid());
            return contract;
        }).collect(Collectors.toList());
    }

    @GetMapping(value = "/web/encounterTypes")
    @ResponseBody
    public List<OperationalEncounterType> encounterTypes() {
        return operationalEncounterTypeRepository.findAll();
    }

    @GetMapping(value = "/web/encounterType/{id}")
    @ResponseBody
    public ResponseEntity getOne(@PathVariable("id") Long id) {
        OperationalEncounterType operationalEncounterType = operationalEncounterTypeRepository.findOne(id);
        if (operationalEncounterType.isVoided())
            return ResponseEntity.notFound().build();
        EncounterTypeContractWeb encounterTypeContractWeb = EncounterTypeContractWeb.fromOperationalEncounterType(operationalEncounterType);
        return new ResponseEntity<>(encounterTypeContractWeb, HttpStatus.OK);
    }

    @GetMapping(value = "/web/encounterTypeDetails/{uuid}")
    @ResponseBody
    public ResponseEntity getOne(@PathVariable("uuid") String uuid) {
        EncounterType encounterType = encounterTypeRepository.findByUuid(uuid);
        if (encounterType.isVoided())
            return ResponseEntity.notFound().build();
        EntityTypeContract entityTypeContract = EntityTypeContract.fromEncounterType(encounterType);
        return new ResponseEntity<>(entityTypeContract, HttpStatus.OK);
    }


    @PostMapping(value = "/web/encounterType")
    @Transactional
    ResponseEntity saveEncounterTypeForWeb(@RequestBody EncounterTypeContractWeb request) {
        accessControlService.checkPrivilege(PrivilegeType.EditEncounterType);
        EncounterType existingEncounterType =
                encounterTypeRepository.findByNameIgnoreCase(request.getName());
        OperationalEncounterType existingOperationalEncounterType =
                operationalEncounterTypeRepository.findByNameIgnoreCase(request.getName());
        if (existingEncounterType != null || existingOperationalEncounterType != null)
            return ResponseEntity.badRequest().body(
                    ReactAdminUtil.generateJsonError(String.format("EncounterType %s already exists", request.getName()))
            );
        EncounterType encounterType = new EncounterType();
        encounterType.assignUUID(request.getUUID());
        buildEncounter(encounterType, request);
        encounterTypeRepository.save(encounterType);
        OperationalEncounterType operationalEncounterType = new OperationalEncounterType();
        operationalEncounterType.assignUUID();
        operationalEncounterType.setName(request.getName());
        operationalEncounterType.setEncounterType(encounterType);
        operationalEncounterTypeRepository.save(operationalEncounterType);

        saveFormsAndMapping(request, encounterType);

        return ResponseEntity.ok(EncounterTypeContractWeb.fromOperationalEncounterType(operationalEncounterType));
    }

    @PutMapping(value = "/web/encounterType/{id}")
    @Transactional
    public ResponseEntity updateEncounterTypeForWeb(@RequestBody EncounterTypeContractWeb request,
                                                    @PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditEncounterType);
        logger.info(String.format("Processing Subject Type update request: %s", request.toString()));
        if (request.getName().trim().equals(""))
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError("Name can not be empty"));

        OperationalEncounterType operationalEncounterType = operationalEncounterTypeRepository.findOne(id);

        if (operationalEncounterType == null)
            return ResponseEntity.badRequest()
                    .body(ReactAdminUtil.generateJsonError(String.format("Subject Type with id '%d' not found", id)));

        EncounterType encounterType = operationalEncounterType.getEncounterType();

        FormMapping existingPEMapping = formMappingService.find(encounterType, FormType.ProgramEncounter);
        FormMapping existingPCMapping = formMappingService.find(encounterType, FormType.ProgramEncounterCancellation);
        boolean isProgramRemoval = (existingPEMapping != null || existingPCMapping != null) && request.getProgramUuid() == null;

        FormMapping existingGEMapping = formMappingService.find(encounterType, FormType.Encounter);
        FormMapping existingECMapping = formMappingService.find(encounterType, FormType.IndividualEncounterCancellation);
        boolean isProgramAddition = (existingGEMapping != null || existingECMapping != null) && request.getProgramUuid() != null;

        if (isProgramRemoval) {
            ResponseEntity validationError = validateFormTransition(request, existingPEMapping, existingPCMapping,
                    "The program cannot be removed because %s associated with more than one encounter type.");
            if (validationError != null) return validationError;
        }

        if (isProgramAddition) {
            ResponseEntity validationError = validateFormTransition(request, existingGEMapping, existingECMapping,
                    "A program cannot be added because %s associated with more than one encounter type.");
            if (validationError != null) return validationError;
        }

        buildEncounter(encounterType, request);
        encounterTypeRepository.save(encounterType);

        operationalEncounterType.setName(request.getName());
        operationalEncounterTypeRepository.save(operationalEncounterType);

        if (isProgramRemoval) {
            handleFormTransition(request, existingPEMapping, existingPCMapping, FormType.Encounter, FormType.IndividualEncounterCancellation, null);
        }

        if (isProgramAddition) {
            handleFormTransition(request, existingGEMapping, existingECMapping, FormType.ProgramEncounter, FormType.ProgramEncounterCancellation, programRepository.findByUuid(request.getProgramUuid()));
        }

        saveFormsAndMapping(request, encounterType);

        return ResponseEntity.ok(EncounterTypeContractWeb.fromOperationalEncounterType(operationalEncounterType));
    }

    private void buildEncounter(EncounterType encounterType, EncounterTypeContractWeb request) {
        encounterType.setName(request.getName());
        encounterType.setEncounterEligibilityCheckRule(request.getEncounterEligibilityCheckRule());
        encounterType.setEncounterEligibilityCheckDeclarativeRule(request.getEncounterEligibilityCheckDeclarativeRule());
        encounterType.setActive(request.getActive());
        encounterType.setImmutable(request.isImmutable());
    }

    private void saveFormsAndMapping(EncounterTypeContractWeb request, EncounterType encounterType) {
        FormType encounterFormType = request.getProgramUuid() == null ?
                FormType.Encounter : FormType.ProgramEncounter;
        FormType cancellationFormType = request.getProgramUuid() == null ?
                FormType.IndividualEncounterCancellation : FormType.ProgramEncounterCancellation;

        Form encounterForm = formService.getOrCreateForm(request.getProgramEncounterFormUuid(), String.format("%s Encounter", encounterType.getName()), encounterFormType);
        formMappingService.saveFormMapping(new FormMappingParameterObject(request.getSubjectTypeUuid(), request.getProgramUuid(), encounterType.getUuid()),
                encounterForm, request.isPerformEncounterApprovalEnabled());

        Form cancellationForm = formService.getOrCreateForm(request.getProgramEncounterCancelFormUuid(), String.format("%s Encounter Cancellation", encounterType.getName()), cancellationFormType);
        formMappingService.saveFormMapping(new FormMappingParameterObject(request.getSubjectTypeUuid(), request.getProgramUuid(), encounterType.getUuid()),
                cancellationForm, request.isCancelEncounterApprovalEnabled());
    }

    @DeleteMapping(value = "/web/encounterType/{id}")
    @Transactional
    public ResponseEntity voidEncounterType(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditEncounterType);
        OperationalEncounterType operationalEncounterType = operationalEncounterTypeRepository.findOne(id);
        if (operationalEncounterType == null)
            return ResponseEntity.notFound().build();
        EncounterType encounterType = operationalEncounterType.getEncounterType();
        if (encounterType == null)
            return ResponseEntity.notFound().build();

        operationalEncounterType.setName(EntityUtil.getVoidedName(operationalEncounterType.getName(), operationalEncounterType.getId()));
        operationalEncounterType.setVoided(true);
        encounterType.setName(EntityUtil.getVoidedName(encounterType.getName(), encounterType.getId()));
        encounterType.setVoided(true);
        operationalEncounterTypeRepository.save(operationalEncounterType);
        encounterTypeRepository.save(encounterType);

        formMappingService.voidExistingFormMappings(new FormMappingParameterObject(null, null, encounterType.getUuid()), null);

        return ResponseEntity.ok(null);
    }

    private ResponseEntity validateFormTransition(EncounterTypeContractWeb request,
                                                  FormMapping existingEncounterMapping,
                                                  FormMapping existingECancellationMapping,
                                                  String errorMessageTemplate) {
        List<String> sharedFormNames = new java.util.ArrayList<>();
        if (existingEncounterMapping != null && isSharedForm(existingEncounterMapping, request.getProgramEncounterFormUuid())) {
            sharedFormNames.add(existingEncounterMapping.getForm().getName());
        }
        if (existingECancellationMapping != null && isSharedForm(existingECancellationMapping, request.getProgramEncounterCancelFormUuid())) {
            sharedFormNames.add(existingECancellationMapping.getForm().getName());
        }
        if (!sharedFormNames.isEmpty()) {
            String formsText;
            if (sharedFormNames.size() == 1) {
                formsText = String.format("the form \"%s\" is", sharedFormNames.get(0));
            } else {
                formsText = String.format("the forms \"%s\" and \"%s\" are", sharedFormNames.get(0), sharedFormNames.get(1));
            }
            return ResponseEntity.badRequest().body(
                    ReactAdminUtil.generateJsonError(String.format(errorMessageTemplate, formsText)));
        }
        return null;
    }

    private boolean isSharedForm(FormMapping existingMapping, String newFormUuid) {
        Form existingForm = existingMapping.getForm();
        if (newFormUuid == null || !newFormUuid.equals(existingForm.getUuid())) {
            return false;
        }
        List<FormMapping> nonVoidedMappings = formMappingRepository.findByFormIdAndIsVoidedFalse(existingForm.getId());
        return nonVoidedMappings.size() > 1;
    }

    private void handleFormTransition(EncounterTypeContractWeb request,
                                      FormMapping existingEncounterMapping,
                                      FormMapping existingCancellationMapping,
                                      FormType targetEncounterFormType,
                                      FormType targetCancellationFormType,
                                      Program program) {
        if (existingEncounterMapping != null) {
            convertFormAndUpdateMapping(existingEncounterMapping, request.getProgramEncounterFormUuid(), targetEncounterFormType, program);
            if (request.getProgramEncounterFormUuid() == null) {
                request.setProgramEncounterFormUuid(existingEncounterMapping.getForm().getUuid());
            }
        }
        if (existingCancellationMapping != null) {
            convertFormAndUpdateMapping(existingCancellationMapping, request.getProgramEncounterCancelFormUuid(), targetCancellationFormType, program);
            if (request.getProgramEncounterCancelFormUuid() == null) {
                request.setProgramEncounterCancelFormUuid(existingCancellationMapping.getForm().getUuid());
            }
        }
    }

    private void convertFormAndUpdateMapping(FormMapping existingMapping, String newFormUuid, FormType targetFormType, Program program) {
        Form existingForm = existingMapping.getForm();
        boolean isSameForm = newFormUuid != null && newFormUuid.equals(existingForm.getUuid());
        if (isSameForm) {
            existingForm.setFormType(targetFormType);
            formRepository.saveAndFlush(existingForm);
            existingMapping.setProgram(program);
        } else {
            existingMapping.setVoided(true);
        }
        formMappingRepository.saveFormMapping(existingMapping);
    }
}
