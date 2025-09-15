package org.avni.server.web;

import org.avni.server.domain.StorageManagementConfig;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.StorageManagementConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.contract.StorageManagementConfigContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StorageManagementConfigController {
    private final StorageManagementConfigService storageManagementConfigService;
    private final AccessControlService accessControlService;

    @Autowired
    public StorageManagementConfigController(StorageManagementConfigService storageManagementConfigService, AccessControlService accessControlService) {
        this.storageManagementConfigService = storageManagementConfigService;
        this.accessControlService = accessControlService;
    }

    @PostMapping(value = "/web/storageManagementConfig")
    public ResponseEntity createOrUpdateStorageManagementConfig(@RequestBody StorageManagementConfigContract storageManagementConfig) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        try {
            StorageManagementConfig savedConfig = storageManagementConfigService.saveOrUpdate(storageManagementConfig);
            return new ResponseEntity<>(savedConfig, HttpStatus.CREATED);
        } catch (ValidationException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "/web/storageManagementConfig")
    public ResponseEntity<StorageManagementConfigContract> getStorageManagementConfig() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        StorageManagementConfig storageManagementConfig = storageManagementConfigService.getStorageManagementConfig();
        return new ResponseEntity<>(storageManagementConfig != null ? storageManagementConfigService.toContract(storageManagementConfig) : null, HttpStatus.OK);
    }
}
