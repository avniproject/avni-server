package org.avni.server.web;


import org.avni.server.application.projections.LocationProjection;
import org.avni.server.builder.BuilderException;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.LocationSyncRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.LocationService;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.AddressLevelContractWeb;
import org.avni.server.web.request.LocationContract;
import org.avni.server.web.request.LocationEditContract;
import org.avni.server.web.request.webapp.search.LocationSearchRequest;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RepositoryRestController
public class LocationController implements RestControllerResourceProcessor<AddressLevel> {

    private final LocationRepository locationRepository;
    private final Logger logger;
    private final UserService userService;
    private final LocationService locationService;
    private final ScopeBasedSyncService<AddressLevel> scopeBasedSyncService;
    private final AccessControlService accessControlService;
    private final LocationSyncRepository locationSyncRepository;

    @Autowired
    public LocationController(LocationRepository locationRepository, UserService userService, LocationService locationService, ScopeBasedSyncService<AddressLevel> scopeBasedSyncService, AccessControlService accessControlService, LocationSyncRepository locationSyncRepository) {
        this.locationRepository = locationRepository;
        this.userService = userService;
        this.locationService = locationService;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.accessControlService = accessControlService;
        this.locationSyncRepository = locationSyncRepository;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/locations", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<?> save(@RequestBody List<LocationContract> locationContracts) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocation);
        try {
            List<AddressLevel> list = locationService.saveAll(locationContracts);
            if (list.size() == 1) {
                return new ResponseEntity<>(list.get(0), HttpStatus.CREATED);
            }
        } catch (BuilderException e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(e.getMessage()));
        }
        return ResponseEntity.ok(null);
    }

    @GetMapping(value = "/locations")
    @ResponseBody
    public Page<LocationProjection> getAll(Pageable pageable) {
        return locationRepository.findNonVoidedLocations(pageable);
    }

    @GetMapping(value = "locations/search/find")
    @ResponseBody
    public Page<LocationProjection> find(
            @RequestParam(value = "title", defaultValue = "") String title,
            @RequestParam(value = "typeId", required = false) Integer typeId,
            @RequestParam(value = "parentId", required = false) Integer parentId,
            Pageable pageable) {
        return locationService.find(new LocationSearchRequest(title, typeId, parentId, pageable));
    }

    @GetMapping(value = "locations/search/findAsList")
    @ResponseBody
    public List<LocationProjection> findAsList(
        @RequestParam(value = "title", defaultValue = "") String title,
        @RequestParam(value = "typeId", required = false) Integer typeId) {
        return locationRepository.findLocationProjectionByTitleIgnoreCaseAndTypeIdAsList(title, typeId);
    }

    @GetMapping(value = "/locations/search/findAllById")
    @ResponseBody
    public List<LocationProjection> findByIdIn(@Param("ids") Long[] ids) {
        if (ids == null || ids.length == 0) {
            return new ArrayList<>();
        }
        return locationRepository.findByIdIn(ids);
    }

    @RequestMapping(value = {"/locations/search/lastModified", "/locations/search/byCatchmentAndLastModified"}, method = RequestMethod.GET)
    @ResponseBody
    public PagedResources<Resource<AddressLevel>> getAddressLevelsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(scopeBasedSyncService.getSyncResultsByCatchment(locationSyncRepository, userService.getCurrentUser(), lastModifiedDateTime, now, pageable, SyncEntityName.Location));
    }

    @PutMapping(value = "/locations/{id}")
    @Transactional
    public ResponseEntity updateLocation(@RequestBody LocationEditContract locationEditContract,
                                         @PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocation);
        logger.info(String.format("Processing location update request: %s", locationEditContract.toString()));
        AddressLevel location;
        try {
            location = locationService.update(locationEditContract, id);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(e.getMessage()));
        }
        return new ResponseEntity<>(location, HttpStatus.OK);
    }

    @DeleteMapping(value = "/locations/{id}")
    @Transactional
    public ResponseEntity voidLocation(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocation);
        AddressLevel location = locationRepository.findOne(id);
        if (location == null)
            return ResponseEntity.badRequest().body(String.format("Location with id '%d' not found", id));

        if (!location.getNonVoidedSubLocations().isEmpty())
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(
                    String.format("Cannot delete location '%s' until all sub locations are deleted", location.getTitle()))
            );

        location.setTitle(String.format("%s (voided~%d)", location.getTitle(), location.getId()));
        location.setVoided(true);
        location.updateAudit();
        locationRepository.save(location);

        return ResponseEntity.ok(null);
    }

    @GetMapping(value = "/locations/search/typeId/{typeId}")
    @ResponseBody
    public List<AddressLevelContractWeb> getLocationsByTypeId(@PathVariable("typeId") Long typeId) {
        accessControlService.checkPrivilege(PrivilegeType.EditLocation);
        return locationRepository.findNonVoidedLocationsByTypeId(typeId).stream()
                .map(AddressLevelContractWeb::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "locations/parents/{uuid}")
    @ResponseBody
    public List<LocationProjection> getParents(@PathVariable("uuid") String uuid,
                                               @RequestParam(value = "maxLevelTypeId", required = false) Long maxLevelTypeId) {
        return locationService.getParents(uuid, maxLevelTypeId);
    }


    @GetMapping(value = "/locations/web")
    @ResponseBody
    public ResponseEntity getLocationByParam(@RequestParam("uuid") String uuid) {
        LocationProjection addressLevel = locationRepository.findNonVoidedLocationsByUuid(uuid);
        if (addressLevel == null) {
            return ResponseEntity.notFound().build();
        }
        return new ResponseEntity<>(AddressLevelContractWeb.fromEntity(addressLevel), HttpStatus.OK);
    }
}
