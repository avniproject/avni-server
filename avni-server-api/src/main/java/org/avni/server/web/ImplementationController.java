package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.organisation.OrganisationCategory;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.BundleService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.OrganisationService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;

@RestController
public class ImplementationController implements RestControllerResourceProcessor<Concept> {
    private final OrganisationService organisationService;
    private final OrganisationConfigService organisationConfigService;
    private final AccessControlService accessControlService;
    private final UserService userService;
    private final BundleService bundleService;

    @Autowired
    public ImplementationController(OrganisationService organisationService, OrganisationConfigService organisationConfigService, AccessControlService accessControlService, UserService userService, BundleService bundleService) {
        this.organisationService = organisationService;
        this.organisationConfigService = organisationConfigService;
        this.accessControlService = accessControlService;
        this.userService = userService;
        this.bundleService = bundleService;
    }

    @RequestMapping(value = "/implementation/export/{includeLocations}", method = RequestMethod.GET)
    public ResponseEntity<ByteArrayResource> export(@PathVariable boolean includeLocations) throws Exception {
        accessControlService.checkPrivilege(PrivilegeType.DownloadBundle);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        ByteArrayOutputStream baos = bundleService.createBundle(organisation, includeLocations);
        byte[] baosByteArray = baos.toByteArray();

        return ResponseEntity.ok()
                .headers(getHttpHeaders())
                .contentLength(baosByteArray.length)
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(new ByteArrayResource(baosByteArray));

    }

    @RequestMapping(value = "/implementation/delete", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity delete(@Param("deleteMetadata") boolean deleteMetadata,
                                 @Param("deleteAdminConfig") boolean deleteAdminConfig) {
        if (userService.isAdmin(UserContextHolder.getUser())) {
            return new ResponseEntity<>("Super admin cannot delete implementation data", HttpStatus.FORBIDDEN);
        }
        Organisation organisation = organisationService.getCurrentOrganisation();
        if (OrganisationCategory.Production.equals(organisation.getCategory().getName())) {
            return new ResponseEntity<>("Production organisation's data cannot be deleted", HttpStatus.CONFLICT);
        }
        if(deleteAdminConfig && !deleteMetadata) {
            return new ResponseEntity<>("You cannot delete admin config data without deleting metadata", HttpStatus.BAD_REQUEST);
        }
        //Delete
        checkPrivilegeAndDeleteTransactionalData(organisation);
        selectivelyCleanupMediaContent(deleteMetadata);
        checkPrivilegeAndDeleteMetadata(deleteMetadata, organisation);
        checkPrivilegeAndDeleteAdminConfig(deleteAdminConfig, organisation);
        //Recreate
        checkPrivilegeAndRecreateBasicAdminConfig(deleteAdminConfig);
        checkPrivilegeAndRecreateBasicMetadata(deleteMetadata);
        //Refer to OrganisationService git history for list of repos and tables excluded from deletion flow due to valid causes
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void checkPrivilegeAndRecreateBasicMetadata(boolean deleteMetadata) {
        if (deleteMetadata) {
            accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
            organisationService.setupBaseOrganisationMetadata(UserContextHolder.getOrganisation());
        }
    }

    private void checkPrivilegeAndRecreateBasicAdminConfig(boolean deleteAdminConfig) {
        if (deleteAdminConfig) {
            accessControlService.checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
            organisationService.setupBaseOrganisationAdminConfig(UserContextHolder.getOrganisation());
        }
    }

    private void checkPrivilegeAndDeleteAdminConfig(boolean deleteAdminConfig, Organisation organisation) {
        if(deleteAdminConfig){
            accessControlService.checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
            organisationService.deleteAdminConfigData(organisation);
        }
    }

    private void selectivelyCleanupMediaContent(boolean deleteMetadata) {
        if (deleteMetadata) {
            accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
        } else {
            accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        }
        organisationService.deleteMediaContent(deleteMetadata);
    }

    private void checkPrivilegeAndDeleteMetadata(boolean deleteMetadata, Organisation organisation) {
        if (deleteMetadata) {
            accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
            organisationService.deleteETLData(organisation);
            organisationConfigService.deleteMetadataRelatedSettings();
            organisationService.deleteMetadata(organisation);
        }
    }

    private void checkPrivilegeAndDeleteTransactionalData(Organisation organisation) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        organisationService.deleteTransactionalData(organisation);
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=impl.zip");
        header.add("Cache-Control", "no-cache, no-store, must-revalidate");
        header.add("Pragma", "no-cache");
        header.add("Expires", "0");
        return header;
    }
}
