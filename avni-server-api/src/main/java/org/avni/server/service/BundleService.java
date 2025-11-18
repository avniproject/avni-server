package org.avni.server.service;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.RoleSwitchableRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

@Component
public class BundleService extends RoleSwitchableRepository {
    private final OrganisationService organisationService;
    private final OrganisationRepository organisationRepository;

    @Autowired
    public BundleService(EntityManager entityManager, OrganisationService organisationService, OrganisationRepository organisationRepository) {
        super(entityManager);
        this.organisationService = organisationService;
        this.organisationRepository = organisationRepository;
    }

    public ByteArrayOutputStream createBundle(Organisation organisation, boolean includeLocations) throws IOException {
        Long orgId = organisation.getId();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //ZipOutputStream will be automatically closed because we are using try-with-resources.
        /**
         * IMPORTANT: The un-tampered bundle is processed in the order of files inserted while generating the bundle,
         * which is as per below code.
         *
         * Always ensure that bundle is created with content in the same sequence that you want it to be processed during upload.
         * DISCLAIMER: If the bundle is tampered, for example to remove any forms or concepts, then the sequence of processing of bundle files is unknown
         */
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            organisationService.addAddressLevelTypesJson(orgId, zos);
            if (includeLocations) {
                organisationService.addAddressLevelsJson(orgId, zos);
                organisationService.addCatchmentsJson(organisation, zos);
            }
            organisationService.addSubjectTypesJson(orgId, zos);
            organisationService.addOperationalSubjectTypesJson(organisation, zos);
            organisationService.addEncounterTypesJson(organisation, zos);
            organisationService.addOperationalEncounterTypesJson(organisation, zos);
            organisationService.addProgramsJson(organisation, zos);
            organisationService.addOperationalProgramsJson(organisation, zos);
            organisationService.addConceptsJson(orgId, zos);
            organisationService.addFormsJson(orgId, zos);
            organisationService.addFormMappingsJson(orgId, zos);
            organisationService.addOrganisationConfig(orgId, zos);
            //Id source is mapped to a catchment so if includeLocations is false we don't add those sources to json
            organisationService.addIdentifierSourceJson(zos, includeLocations);
            organisationService.addRelationJson(zos);
            organisationService.addRelationShipTypeJson(zos);
            organisationService.addChecklistDetailJson(zos);
            organisationService.addGroupsJson(zos);
            organisationService.addGroupRoleJson(zos);
            organisationService.addGroupPrivilegeJson(zos);
            organisationService.addVideoJson(zos);
            organisationService.addReportCards(zos);
            organisationService.addReportDashboard(zos);
            organisationService.addGroupDashboardJson(zos);
            organisationService.addDocumentation(zos);
            organisationService.addTaskType(zos);
            organisationService.addTaskStatus(zos);
            organisationService.addSubjectTypeIcons(zos);
            organisationService.addReportCardIcons(zos);
            organisationService.addConceptMedia(zos);
            organisationService.addApplicationMenus(zos);
            organisationService.addMessageRules(zos);
            organisationService.addTranslations(orgId, zos);
            organisationService.addOldRuleDependency(orgId, zos);
            organisationService.addOldRules(orgId, zos);
            organisationService.addCustomQueries(orgId,zos);
        }
        return baos;
    }

    @Transactional
    public ByteArrayOutputStream generateBundleForOrg(Long organisationId) throws IOException {
        Organisation currentContextOrg = UserContextHolder.getOrganisation();
        try {
            setRoleToNone();
            Organisation org = organisationRepository.findOne(organisationId);
            UserContextHolder.getUserContext().setOrganisation(org);
            setRoleToOtherUser(org.getDbUser());
            return createBundle(org, true);
        } finally {
            setRoleBackToUser();
            UserContextHolder.getUserContext().setOrganisation(currentContextOrg);
        }
    }
}
