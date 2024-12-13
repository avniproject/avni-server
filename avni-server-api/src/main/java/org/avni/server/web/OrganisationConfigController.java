package org.avni.server.web;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.sync.attributes.SyncAttributesJobListener;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.OrganisationConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.transaction.Transactional;

@RepositoryRestController
public class OrganisationConfigController implements RestControllerResourceProcessor<OrganisationConfig> {
    private final OrganisationConfigService organisationConfigService;
    private final AccessControlService accessControlService;
    private static final Logger logger = LoggerFactory.getLogger(OrganisationConfigController.class);


    @Autowired
    public OrganisationConfigController(OrganisationConfigService organisationConfigService, AccessControlService accessControlService) {
        this.organisationConfigService = organisationConfigService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/organisationConfig", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity save(@RequestBody OrganisationConfigRequest request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        OrganisationConfig organisationConfig = organisationConfigService.saveOrganisationConfig(request, organisation);
        return new ResponseEntity<>(organisationConfig, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/organisationConfig", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity update(@RequestBody OrganisationConfigRequest request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(organisation);
        if (organisationConfig == null ) {
            return save(request);
        } else {
            try {
                organisationConfigService.updateOrganisationConfig(request, organisationConfig);
                return new ResponseEntity<>(organisationConfig, HttpStatus.OK);
            } catch(Exception e) {
                logger.error("Error updating organisationConfig", e);
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
    }

    @RequestMapping(value = "/organisationConfig/exportSettings", method = RequestMethod.GET)
    public JsonObject getExportSettings() {
        return organisationConfigService.getExportSettings();
    }

    @RequestMapping(value = "/organisationConfig/exportSettings", method = RequestMethod.POST)
    public ResponseEntity<?> saveNewExportSettings(@RequestParam(value = "name") String name, @RequestBody JsonObject request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return organisationConfigService.saveNewExportSettings(name, request);
    }

    @RequestMapping(value = "/organisationConfig/exportSettings", method = RequestMethod.PUT)
    public ResponseEntity<?> updateExistingExportSettings(@RequestParam(value = "name") String name, @RequestBody JsonObject request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return organisationConfigService.updateExistingExportSettings(name, request);
    }

    @RequestMapping(value = "/organisationConfig/exportSettings", method = RequestMethod.DELETE)
    public ResponseEntity<?> deleteExportSettings(@RequestParam(value = "name") String name) {
        accessControlService.checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
        return organisationConfigService.deleteExportSettings(name);
    }
}
