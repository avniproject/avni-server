package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.LocationService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.AddressLevelTypeContract;
import org.avni.server.web.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AddressLevelTypeController extends AbstractController<AddressLevelType> {
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final Logger logger;
    private final LocationService locationService;
    private final ProjectionFactory projectionFactory;
    private final AccessControlService accessControlService;
    private final OrganisationConfigService organisationConfigService;

    @Autowired
    public AddressLevelTypeController(AddressLevelTypeRepository addressLevelTypeRepository, LocationService locationService, ProjectionFactory projectionFactory, AccessControlService accessControlService, OrganisationConfigService organisationConfigService) {
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.locationService = locationService;
        this.projectionFactory = projectionFactory;
        this.accessControlService = accessControlService;
        this.organisationConfigService = organisationConfigService;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @GetMapping(value = "/addressLevelType")
    @ResponseBody
    public Page<AddressLevelType> getAllNonVoidedAddressLevelType(Pageable pageable) {
        return addressLevelTypeRepository.findPageByIsVoidedFalse(pageable);
    }

    @GetMapping(value = "/web/addressLevelType")
    @ResponseBody
    public List<AddressLevelType.AddressLevelTypeProjection> findAll() {
        return addressLevelTypeRepository.findAllByIsVoidedFalse()
                .stream()
                .map(t -> projectionFactory.createProjection(AddressLevelType.AddressLevelTypeProjection.class, t))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/addressLevelType/{id}")
    public ResponseEntity<?> getSingle(@PathVariable Long id) {
        return new ResponseEntity<>(addressLevelTypeRepository.findOne(id), HttpStatus.OK);
    }

    @PostMapping(value = "/addressLevelType")
    @Transactional
    public ResponseEntity<?> createAddressLevelType(@RequestBody AddressLevelTypeContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocationType);
        try {
            //Check if a location type with the same name already exists before creating (case-insensitive check)
            AddressLevelType existingType = locationService.findAddressLevelTypeByName(contract.getName(), null);
            if (existingType != null) {
                return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Location Type with name %s already exists", contract.getName())));
            }

            AddressLevelType addressLevelType = locationService.createAddressLevelType(UserContextHolder.getOrganisation(), contract);
            return new ResponseEntity<>(addressLevelType, HttpStatus.CREATED);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(e.getMessage()));
        }
    }

    @PostMapping(value = "/addressLevelTypes")
    @Transactional
    public ResponseEntity<?> save(@RequestBody List<AddressLevelTypeContract> addressLevelTypeContracts) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocationType);
        try {
            // Check for duplicate names first before processing any contracts
            for (AddressLevelTypeContract contract : addressLevelTypeContracts) {
                AddressLevelType existingType = locationService.findAddressLevelTypeByName(contract.getName(), contract.getUuid());
                if (existingType != null) {
                    return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Location Type with name %s already exists", contract.getName())));
                }
            }

            // If no duplicates found, process all contracts
            for (AddressLevelTypeContract addressLevelTypeContract : addressLevelTypeContracts) {
                logger.info(String.format("Processing addressLevelType request: %s", addressLevelTypeContract.getUuid()));
                locationService.createAddressLevelType(UserContextHolder.getOrganisation(), addressLevelTypeContract);
            }
            return ResponseEntity.ok(null);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(e.getMessage()));
        }
    }

    @PutMapping(value = "/addressLevelType/{id}")
    @Transactional
    public ResponseEntity<?> updateAddressLevelType(@PathVariable("id") Long id, @RequestBody AddressLevelTypeContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocationType);
        try {
            AddressLevelType addressLevelType = addressLevelTypeRepository.findByUuid(contract.getUuid());
            if (addressLevelType == null) {
                return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError("No location type found with the provided UUID"));
            }

            // Case-insensitive check for duplicate names, excluding the current type being updated
            AddressLevelType addressLevelTypeWithSameName = locationService.findAddressLevelTypeByName(contract.getName(), contract.getUuid());
            if (addressLevelTypeWithSameName != null) {
                return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Location Type with name %s already exists", contract.getName())));
            }

            // Use the service method for update
            AddressLevelType updatedAddressLevelType = locationService.createAddressLevelType(UserContextHolder.getOrganisation(), contract);

            // Update audit for all associated address levels
            Set<AddressLevel> addressLevels = updatedAddressLevelType.getAddressLevels();
            addressLevels.forEach(addressLevel -> addressLevel.updateAudit());

            return new ResponseEntity<>(updatedAddressLevelType, HttpStatus.CREATED);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(e.getMessage()));
        }
    }

    @DeleteMapping(value = "/addressLevelType/{id}")
    @Transactional
    public ResponseEntity<?> voidAddressLevelType(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocationType);
        AddressLevelType addressLevelType = addressLevelTypeRepository.findOne(id);
        if (addressLevelType == null) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("AddressLevelType with id %d not found", id)));
        }
        if (!addressLevelType.isVoidable()) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(
                    String.format("Cannot delete '%s'. Sub location types or locations of this type exist. Please delete them to proceed.", addressLevelType.getName())));
        }
        addressLevelType.setName(EntityUtil.getVoidedName(addressLevelType.getName(), addressLevelType.getId()));
        addressLevelType.setVoided(true);

        // Clean up references in custom registration locations
        organisationConfigService.removeVoidedAddressLevelTypeFromCustomRegistrationLocations(UserContextHolder.getOrganisation(), addressLevelType.getUuid());
        return new ResponseEntity<>(addressLevelType, HttpStatus.OK);
    }
}
