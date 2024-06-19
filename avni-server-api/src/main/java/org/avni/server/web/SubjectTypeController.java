package org.avni.server.web;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.GroupRole;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.GroupRoleContract;
import org.avni.server.web.request.SubjectTypeContract;
import org.avni.server.web.request.syncAttribute.UserSyncAttributeAssignmentRequest;
import org.avni.server.web.request.webapp.SubjectTypeContractWeb;
import org.avni.server.web.request.webapp.SubjectTypeSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class SubjectTypeController implements RestControllerResourceProcessor<SubjectTypeContractWeb> {
    private final Logger logger;
    private final OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    private final SubjectTypeService subjectTypeService;
    private final GroupRoleRepository groupRoleRepository;
    private final ResetSyncService resetSyncService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final FormService formService;
    private final FormMappingService formMappingService;
    private final OrganisationConfigService organisationConfigService;
    private final AccessControlService accessControlService;

    @Autowired
    public SubjectTypeController(SubjectTypeRepository subjectTypeRepository,
                                 OperationalSubjectTypeRepository operationalSubjectTypeRepository,
                                 SubjectTypeService subjectTypeService,
                                 GroupRoleRepository groupRoleRepository,
                                 ResetSyncService resetSyncService, FormService formService, FormMappingService formMappingService,
                                 OrganisationConfigService organisationConfigService,
                                 AccessControlService accessControlService) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.operationalSubjectTypeRepository = operationalSubjectTypeRepository;
        this.subjectTypeService = subjectTypeService;
        this.groupRoleRepository = groupRoleRepository;
        this.resetSyncService = resetSyncService;
        this.formService = formService;
        this.formMappingService = formMappingService;
        this.organisationConfigService = organisationConfigService;
        this.accessControlService = accessControlService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/subjectTypes", method = RequestMethod.POST)
    @Transactional
    public void save(@RequestBody List<SubjectTypeContract> subjectTypeRequests) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        subjectTypeRequests.forEach(subjectTypeContract -> {
            SubjectTypeService.SubjectTypeUpsertResponse response = subjectTypeService.saveSubjectType(subjectTypeContract);
            if(response.isSubjectTypeNotPresentInDB() && Subject.valueOf(subjectTypeContract.getType()).equals(Subject.User)) {
                subjectTypeService.launchUserSubjectTypeJob(response.getSubjectType());
            }
        });
    }

    @GetMapping(value = "/web/subjectType")
    @ResponseBody
    public PagedResources<Resource<SubjectTypeContractWeb>> getAll(Pageable pageable) {
        return wrap(operationalSubjectTypeRepository
                .findPageByIsVoidedFalse(pageable)
                .map((OperationalSubjectType operationalSubjectType) -> {
                    FormMapping formMapping = formMappingService.find(operationalSubjectType.getSubjectType());
                    return SubjectTypeContractWeb.fromOperationalSubjectType(operationalSubjectType, formMapping);
                }));
    }

    @GetMapping(value = "/web/subjectType/{id}")
    @ResponseBody
    public ResponseEntity getOne(@PathVariable("id") Long id) {
        OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository.findOne(id);
        if (operationalSubjectType.isVoided())
            return ResponseEntity.notFound().build();
        FormMapping formMapping = formMappingService.find(operationalSubjectType.getSubjectType());
        SubjectTypeContractWeb subjectTypeContractWeb = SubjectTypeContractWeb.fromOperationalSubjectType(operationalSubjectType, formMapping);
        OrganisationConfig organisationConfig = organisationConfigService.getCurrentOrganisationConfig();
        List<SubjectTypeSetting> customRegistrationLocations = organisationConfig.getCustomRegistrationLocations();
        Optional<List<String>> locationUUIDs = customRegistrationLocations
                .stream()
                .filter(s -> s.getSubjectTypeUUID().equals(operationalSubjectType.getSubjectTypeUUID()))
                .map(SubjectTypeSetting::getLocationTypeUUIDs)
                .findFirst();
        subjectTypeContractWeb.setLocationTypeUUIDs(locationUUIDs.orElse(null));
        return new ResponseEntity<>(subjectTypeContractWeb, HttpStatus.OK);
    }

    @PostMapping(value = "/web/subjectType")
    @Transactional
    public ResponseEntity saveSubjectTypeForWeb(@RequestBody SubjectTypeContractWeb request) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        SubjectType existingSubjectType =
                subjectTypeRepository.findByNameIgnoreCase(request.getName());
        OperationalSubjectType existingOperationalSubjectType =
                operationalSubjectTypeRepository.findByNameIgnoreCase(request.getName());
        if (existingSubjectType != null || existingOperationalSubjectType != null)
            return ResponseEntity.badRequest().body(
                    ReactAdminUtil.generateJsonError(String.format("SubjectType %s already exists", request.getName()))
            );
        if (request.getType() == null) {
            return ResponseEntity.badRequest().body(
                    ReactAdminUtil.generateJsonError("Can't save subjectType with empty type")
            );
        }
        SubjectType subjectType = new SubjectType();
        subjectType.assignUUID();
        buildSubjectType(request, subjectType);
        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.assignUUID();
        operationalSubjectType.setName(request.getName());
        operationalSubjectType.setSubjectType(subjectType);
        operationalSubjectTypeRepository.save(operationalSubjectType);

        formMappingService.saveFormMapping(
                new FormMappingParameterObject(subjectType.getUuid(), null, null),
                formService.getOrCreateForm(request.getRegistrationFormUuid(),
                        String.format("%s Registration", subjectType.getName()),
                        FormType.IndividualProfile), request.isEnableRegistrationApproval());

        organisationConfigService.saveCustomRegistrationLocations(request.getLocationTypeUUIDs(), subjectType);
        FormMapping formMapping = formMappingService.find(subjectType);
        SubjectTypeContractWeb subjectTypeContractWeb = SubjectTypeContractWeb.fromOperationalSubjectType(operationalSubjectType, formMapping);
        subjectTypeContractWeb.setLocationTypeUUIDs(request.getLocationTypeUUIDs());

        if (subjectType.getType().equals(Subject.User))
            subjectTypeService.launchUserSubjectTypeJob(subjectType);
        return ResponseEntity.ok(subjectTypeContractWeb);
    }

    private void buildSubjectType(@RequestBody SubjectTypeContractWeb request, SubjectType subjectType) {
        subjectType.setName(request.getName());
        subjectType.setActive(request.getActive());
        subjectType.setAllowEmptyLocation(request.isAllowEmptyLocation());
        subjectType.setAllowProfilePicture(request.isAllowProfilePicture());
        subjectType.setUniqueName(request.isUniqueName());
        subjectType.setType(Subject.valueOf(request.getType()));
        subjectType.setSubjectSummaryRule(request.getSubjectSummaryRule());
        subjectType.setProgramEligibilityCheckRule(request.getProgramEligibilityCheckRule());
        subjectType.setAllowMiddleName(request.isAllowMiddleName());
        subjectType.setValidFirstNameFormat(request.getValidFirstNameFormat());
        subjectType.setValidMiddleNameFormat(request.getValidMiddleNameFormat());
        subjectType.setLastNameOptional(request.isLastNameOptional());
        subjectType.setValidLastNameFormat(request.getValidLastNameFormat());
        subjectType.setIconFileS3Key(request.getIconFileS3Key());
        subjectType.setShouldSyncByLocation(request.isShouldSyncByLocation());
        subjectType.setDirectlyAssignable(request.isDirectlyAssignable());
        subjectType.setSyncRegistrationConcept1(request.getSyncRegistrationConcept1());
        subjectType.setSyncRegistrationConcept2(request.getSyncRegistrationConcept2());
        subjectType.setNameHelpText(request.getNameHelpText());
        subjectType.setSettings(request.getSettings() != null ? request.getSettings() : subjectTypeService.getDefaultSettings());
        SubjectType savedSubjectType = subjectTypeRepository.save(subjectType);
        if (Subject.Household.toString().equals(request.getType())) {
            subjectType.setGroup(true);
            subjectType.setHousehold(true);
            saveGroupRoles(savedSubjectType, request.getGroupRoles());
        }
        if (Subject.Group.toString().equals(request.getType())) {
            subjectType.setGroup(true);
            saveGroupRoles(savedSubjectType, request.getGroupRoles());
        }
    }

    @PutMapping(value = "/web/subjectType/{id}")
    public ResponseEntity updateSubjectTypeForWeb(@RequestBody SubjectTypeContractWeb request,
                                                  @PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        logger.info(String.format("Processing Subject Type update request: %s", request.toString()));
        if (request.getName().trim().equals(""))
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError("Name can not be empty"));

        OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository.findOne(id);

        if (operationalSubjectType == null)
            return ResponseEntity.badRequest()
                    .body(ReactAdminUtil.generateJsonError(String.format("Subject Type with id '%d' not found", id)));
        SubjectType subjectType = operationalSubjectType.getSubjectType();
        boolean isSyncConcept1Changed = !Objects.equals(request.getSyncRegistrationConcept1(), subjectType.getSyncRegistrationConcept1());
        boolean isSyncConcept2Changed = !Objects.equals(request.getSyncRegistrationConcept2(), subjectType.getSyncRegistrationConcept2());
        if (isSyncConcept1Changed)
            subjectType.setSyncRegistrationConcept1Usable(false);
        if (isSyncConcept2Changed)
            subjectType.setSyncRegistrationConcept2Usable(false);
        resetSyncService.recordSyncAttributeChange(operationalSubjectType.getSubjectType(), request);
        updateSubjectType(request, operationalSubjectType);
        subjectTypeService.updateSyncAttributesIfRequired(subjectType);
        FormMapping formMapping = formMappingService.find(subjectType);
        SubjectTypeContractWeb subjectTypeContractWeb = SubjectTypeContractWeb.fromOperationalSubjectType(operationalSubjectType, formMapping);
        subjectTypeContractWeb.setLocationTypeUUIDs(request.getLocationTypeUUIDs());

        if (subjectType.getType().equals(Subject.User))
            subjectTypeService.launchUserSubjectTypeJob(subjectType);

        return ResponseEntity.ok(subjectTypeContractWeb);
    }

    @Transactional
    public void updateSubjectType(@RequestBody SubjectTypeContractWeb request, OperationalSubjectType operationalSubjectType) {
        SubjectType subjectType = operationalSubjectType.getSubjectType();

        buildSubjectType(request, subjectType);
        operationalSubjectType.setName(request.getName());
        operationalSubjectTypeRepository.save(operationalSubjectType);

        formMappingService.saveFormMapping(
                new FormMappingParameterObject(subjectType.getUuid(), null, null),
                formService.getOrCreateForm(request.getRegistrationFormUuid(),
                        String.format("%s Registration", subjectType.getName()), FormType.IndividualProfile), request.isEnableRegistrationApproval());

        organisationConfigService.saveCustomRegistrationLocations(request.getLocationTypeUUIDs(), subjectType);
    }

    @DeleteMapping(value = "/web/subjectType/{id}")
    @Transactional
    public ResponseEntity voidSubjectType(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository.findOne(id);
        if (operationalSubjectType == null)
            return ResponseEntity.notFound().build();
        SubjectType subjectType = operationalSubjectType.getSubjectType();
        if (subjectType == null)
            return ResponseEntity.notFound().build();

        operationalSubjectType.setName(ReactAdminUtil.getVoidedName(operationalSubjectType.getName(), operationalSubjectType.getId()));
        operationalSubjectType.setVoided(true);
        subjectType.setName(ReactAdminUtil.getVoidedName(subjectType.getName(), subjectType.getId()));
        subjectType.setVoided(true);
        operationalSubjectTypeRepository.save(operationalSubjectType);
        subjectTypeRepository.save(subjectType);
        if (subjectType.isGroup()) {
            voidAllGroupRoles(subjectType);
        }

        formMappingService.voidExistingFormMappings(new FormMappingParameterObject(subjectType.getUuid(), null, null), null);

        return ResponseEntity.ok(null);
    }

    @GetMapping(value = "/subjectType/syncAttributesData")
    public UserSyncAttributeAssignmentRequest getAllConceptSyncAttributes() {
        return subjectTypeService.getSyncAttributeData();
    }

    private void voidAllGroupRoles(SubjectType subjectType) {
        List<GroupRole> groupRoles = groupRoleRepository.findByGroupSubjectType_IdAndIsVoidedFalse(subjectType.getId())
                .stream().peek(groupRole -> groupRole.setVoided(true))
                .collect(Collectors.toList());
        groupRoleRepository.saveAll(groupRoles);
    }

    private void saveGroupRoles(SubjectType groupSubjectType, List<GroupRoleContract> groupRoleContracts) {
        List<GroupRole> groupRoles = groupRoleContracts.stream()
                .map(groupRoleContract -> getGroupRole(groupRoleContract, groupSubjectType))
                .collect(Collectors.toList());
        groupRoleRepository.saveAll(groupRoles);
    }

    private GroupRole getGroupRole(GroupRoleContract groupRoleContract, SubjectType groupSubjectType) {
        GroupRole groupRole = groupRoleRepository.findByUuid(groupRoleContract.getGroupRoleUUID());
        if (groupRole == null) {
            groupRole = new GroupRole();
        }
        groupRole.setUuid(groupRoleContract.getGroupRoleUUID());
        groupRole.setGroupSubjectType(groupSubjectType);
        groupRole.setMemberSubjectType(subjectTypeRepository.findByName(groupRoleContract.getSubjectMemberName()));
        groupRole.setVoided(groupRoleContract.isVoided());
        groupRole.setRole(groupRoleContract.getRole());
        groupRole.setMinimumNumberOfMembers(groupRoleContract.getMinimumNumberOfMembers());
        groupRole.setMaximumNumberOfMembers(groupRoleContract.getMaximumNumberOfMembers());
        return groupRole;
    }
}
