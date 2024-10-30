package org.avni.server.web;

import org.avni.server.builder.BuilderException;
import org.avni.server.dao.CatchmentRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.CatchmentService;
import org.avni.server.service.ResetSyncService;
import org.avni.server.service.S3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.CatchmentContract;
import org.avni.server.web.request.CatchmentsContract;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class CatchmentController implements RestControllerResourceProcessor<CatchmentContract> {
    private final CatchmentRepository catchmentRepository;
    private final LocationRepository locationRepository;
    private final CatchmentService catchmentService;
    private final S3Service s3Service;
    private final ResetSyncService resetSyncService;
    private final AccessControlService accessControlService;
    private static final Logger logger = LoggerFactory.getLogger(CatchmentController.class);
    private final ErrorBodyBuilder errorBodyBuilder;


    @Autowired
    public CatchmentController(CatchmentRepository catchmentRepository,
                               LocationRepository locationRepository,
                               CatchmentService catchmentService,
                               S3Service s3Service,
                               ResetSyncService resetSyncService, AccessControlService accessControlService, ErrorBodyBuilder errorBodyBuilder) {
        this.catchmentRepository = catchmentRepository;
        this.locationRepository = locationRepository;
        this.catchmentService = catchmentService;
        this.s3Service = s3Service;
        this.resetSyncService = resetSyncService;
        this.accessControlService = accessControlService;
        this.errorBodyBuilder = errorBodyBuilder;
    }

    CatchmentController(CatchmentRepository catchmentRepository, LocationRepository locationRepository, CatchmentService catchmentService, S3Service s3Service, ResetSyncService resetSyncService, AccessControlService accessControlService) {
        this(catchmentRepository, locationRepository, catchmentService, s3Service, resetSyncService, accessControlService,
                ErrorBodyBuilder.createForTest());
    }

    @GetMapping(value = "catchment")
    public CollectionModel<EntityModel<CatchmentContract>> get(Pageable pageable) {
        Page<Catchment> all = catchmentRepository.findPageByIsVoidedFalse(pageable);
        Page<CatchmentContract> catchmentContracts = all.map(CatchmentContract::fromEntity);
        return wrap(catchmentContracts);
    }

    @GetMapping(value = "catchment/{id}")
    public EntityModel<CatchmentContract> getById(@PathVariable Long id) {
        Catchment catchment = catchmentRepository.findOne(id);
        CatchmentContract catchmentContract = CatchmentContract.fromEntity(catchment);
        boolean fastSyncExists = s3Service.fileExists(String.format("MobileDbBackup-%s", catchment.getUuid()));
        catchmentContract.setFastSyncExists(fastSyncExists);
        return new EntityModel<>(catchmentContract);
    }

    @GetMapping(value = "catchment/search/findAllById")
    public List<CatchmentContract> getById(@Param("ids") Long[] ids) {
        List<Catchment> catchments = catchmentRepository.findByIdIn(ids);
        return catchments.stream().map(CatchmentContract::fromEntity).collect(Collectors.toList());
    }

    @GetMapping(value = "catchment/search/find")
    public CollectionModel<EntityModel<CatchmentContract>> find(@RequestParam(value = "name") String name, Pageable pageable) {
        Page<Catchment> catchments = catchmentRepository.findByIsVoidedFalseAndNameIgnoreCaseStartingWithOrderByNameAsc(name, pageable);
        Page<CatchmentContract> catchmentContracts = catchments.map(CatchmentContract::fromEntity);
        return wrap(catchmentContracts);
    }

    @PostMapping(value = "/catchment")
    @Transactional
    ResponseEntity<?> createSingleCatchment(@RequestBody @Valid CatchmentContract catchmentContract) throws Exception {
        accessControlService.checkPrivilege(PrivilegeType.EditCatchment);
        if (catchmentRepository.findByName(catchmentContract.getName()) != null)
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Catchment with name %s already exists", catchmentContract.getName())));
        Catchment catchment = new Catchment();
        catchment.assignUUID();
        catchment.setName(catchmentContract.getName());
        for (Long locationId : catchmentContract.getLocationIds()) {
            AddressLevel addressLevel = locationRepository.findOne(locationId);
            if(addressLevel == null)
                throw new Exception(String.format("Location id %d not found", locationId));
            catchment.addAddressLevel(addressLevel);
        }
        catchmentRepository.save(catchment);
        return new ResponseEntity<>(catchment, HttpStatus.CREATED);
    }

    @PutMapping(value ="/catchment/{id}")
    @Transactional
    public ResponseEntity<?> updateCatchment(@PathVariable("id") Long id, @RequestBody CatchmentContract catchmentContract) throws Exception {
        accessControlService.checkPrivilege(PrivilegeType.EditCatchment);
        Catchment catchment = catchmentRepository.findOne(id);
        Catchment catchmentWithSameName = catchmentRepository.findByName(catchmentContract.getName());
        //Do not allow to change catchment name when there is already another catchment with the same name
        if (catchmentWithSameName != null && catchmentWithSameName.getId() != catchment.getId())
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Catchment with name %s already exists", catchmentContract.getName())));
        resetSyncService.recordCatchmentChange(catchment, catchmentContract);
        catchment.setName(catchmentContract.getName());
        catchment.clearAddressLevels();
        for (Long locationId : catchmentContract.getLocationIds()) {
            AddressLevel addressLevel = locationRepository.findOne(locationId);
            if(addressLevel == null)
                throw new Exception(String.format("Location id %d not found", locationId));
            addressLevel.addCatchment(catchment);
        }
        catchment.updateAudit();
        catchmentRepository.save(catchment);
        if (catchmentContract.isFastSyncExists() && catchmentContract.isDeleteFastSync()) {
            s3Service.deleteObject(String.format("MobileDbBackup-%s", catchment.getUuid()));
        }
        return new ResponseEntity<>(catchment, HttpStatus.OK);
    }

    @DeleteMapping(value ="/catchment/{id}")
    @Transactional
    public ResponseEntity<?> voidCatchment(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditCatchment);
        Catchment catchment = catchmentRepository.findOne(id);
        if (catchment == null) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("AddressLevelType with id %d not found", id)));
        }
        catchment.setVoided(true);
        catchment.setName(EntityUtil.getVoidedName(catchment.getName(),catchment.getId()));
        catchmentRepository.save(catchment);
        return new ResponseEntity<>(CatchmentContract.fromEntity(catchment), HttpStatus.OK);
    }

    @RequestMapping(value = "/catchments", method = RequestMethod.POST)
    @Transactional
    ResponseEntity<?> save(@RequestBody CatchmentsContract catchmentsContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditCatchment);
        try {
            Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
            catchmentService.saveAllCatchments(catchmentsContract, organisation);
        } catch (BuilderException e) {
            logger.error("Error saving catchments", e);
            return ResponseEntity.badRequest().body(errorBodyBuilder.getErrorMessageBody(e));
        }
        return ResponseEntity.ok(null);
    }

}
