package org.avni.server.web;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.LocationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.util.ValidationUtil;
import org.avni.server.web.request.AddressLevelTypeContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
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

    @Autowired
    public AddressLevelTypeController(AddressLevelTypeRepository addressLevelTypeRepository, LocationService locationService, ProjectionFactory projectionFactory, AccessControlService accessControlService) {
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.locationService = locationService;
        this.projectionFactory = projectionFactory;
        this.accessControlService = accessControlService;
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
        //Do not allow to create location type when there is already another location type with the same name
        if (addressLevelTypeRepository.findByName(contract.getName()) != null)
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Location Type with name %s already exists", contract.getName())));

        AddressLevelType addressLevelType = locationService.createAddressLevelType(contract);
        return new ResponseEntity<>(addressLevelType, HttpStatus.CREATED);
    }

    @PostMapping(value = "/addressLevelTypes")
    @Transactional
    public ResponseEntity<?> save(@RequestBody List<AddressLevelTypeContract> addressLevelTypeContracts) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocationType);
        for (AddressLevelTypeContract addressLevelTypeContract : addressLevelTypeContracts) {
            logger.info(String.format("Processing addressLevelType request: %s", addressLevelTypeContract.getUuid()));
            locationService.createAddressLevelType(addressLevelTypeContract);
        }
        return ResponseEntity.ok(null);
    }

    @PutMapping(value = "/addressLevelType/{id}")
    @Transactional
    public ResponseEntity<?> updateAddressLevelType(@PathVariable("id") Long id, @RequestBody AddressLevelTypeContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocationType);
        AddressLevelType addressLevelType = addressLevelTypeRepository.findByUuid(contract.getUuid());
        AddressLevelType addressLevelTypeWithSameName = addressLevelTypeRepository.findByName(contract.getName());
        //Do not allow to change location type name when there is already another location type with the same name
        if (addressLevelTypeWithSameName != null && addressLevelTypeWithSameName.getUuid() != addressLevelType.getUuid())
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Location Type with name %s already exists", contract.getName())));

        if (ValidationUtil.checkNullOrEmptyOrContainsDisallowedCharacters(contract.getName(), ValidationUtil.COMMON_INVALID_CHARS_PATTERN)) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Invalid Location Type name %s", contract.getName())));
        }
        addressLevelType.setName(contract.getName());
        if (ValidationUtil.checkNull(contract.getLevel())) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Invalid Location Type level %s", contract.getLevel())));
        }
        addressLevelType.setLevel(contract.getLevel());
        Set<AddressLevel> addressLevels = addressLevelType.getAddressLevels();
        addressLevels.forEach(addressLevel -> addressLevel.updateAudit());
        addressLevelTypeRepository.save(addressLevelType);
        return new ResponseEntity<>(addressLevelType, HttpStatus.CREATED);
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
        addressLevelType.setVoided(true);
        return new ResponseEntity<>(addressLevelType, HttpStatus.OK);
    }
}
