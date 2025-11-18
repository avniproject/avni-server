package org.avni.server.service;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.organisation.OrganisationCategory;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImplementationService {
    private final OrganisationService organisationService;
    private final OrganisationConfigService organisationConfigService;
    private final AccessControlService accessControlService;
    private final UserService userService;

    public ImplementationService(OrganisationService organisationService,
                                OrganisationConfigService organisationConfigService,
                                AccessControlService accessControlService,
                                UserService userService) {
        this.organisationService = organisationService;
        this.organisationConfigService = organisationConfigService;
        this.accessControlService = accessControlService;
        this.userService = userService;
    }

    @Transactional
    public void deleteImplementationData(boolean deleteMetadata, boolean deleteAdminConfig) {
        if (userService.isAdmin(UserContextHolder.getUser())) {
            throw new ValidationException("Super admin cannot delete implementation data");
        }

        Organisation organisation = organisationService.getCurrentOrganisation();
        if (OrganisationCategory.Production.equals(organisation.getCategory().getName())) {
            throw new ValidationException("Production organisation's data cannot be deleted");
        }

        if (deleteAdminConfig && !deleteMetadata) {
            throw new ValidationException("You cannot delete admin config data without deleting metadata");
        }

        // Delete operations
        deleteTransactionalData(organisation);
        cleanupMediaContent(deleteMetadata);
        deleteMetadata(deleteMetadata, organisation);
        deleteAdminConfig(deleteAdminConfig, organisation);

        // Recreate operations
        recreateBasicAdminConfig(deleteAdminConfig);
        recreateBasicMetadata(deleteMetadata);
    }

    private void recreateBasicMetadata(boolean deleteMetadata) {
        if (deleteMetadata) {
            accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
            organisationService.setupBaseOrganisationMetadata(UserContextHolder.getOrganisation());
        }
    }

    private void recreateBasicAdminConfig(boolean deleteAdminConfig) {
        if (deleteAdminConfig) {
            accessControlService.checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
            organisationService.setupBaseOrganisationAdminConfig(UserContextHolder.getOrganisation());
        }
    }

    private void deleteAdminConfig(boolean deleteAdminConfig, Organisation organisation) {
        if (deleteAdminConfig) {
            accessControlService.checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
            organisationService.deleteAdminConfigData(organisation);
        }
    }

    private void cleanupMediaContent(boolean deleteMetadata) {
        if (deleteMetadata) {
            accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
        } else {
            accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        }
        organisationService.deleteMediaContent(deleteMetadata);
    }

    private void deleteMetadata(boolean deleteMetadata, Organisation organisation) {
        if (deleteMetadata) {
            accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
            organisationService.deleteETLData(organisation);
            organisationConfigService.deleteMetadataRelatedSettings();
            organisationService.deleteMetadata(organisation);
        }
    }

    private void deleteTransactionalData(Organisation organisation) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        organisationService.deleteTransactionalData(organisation);
    }
}
