package org.avni.server.web;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.CHSEntity;
import org.avni.server.service.LocationHierarchyService;
import org.avni.server.service.OrganisationConfigService;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
public class LocationHierarchyController implements RestControllerResourceProcessor<AddressLevel> {

    private final LocationRepository locationRepository;
    private final Logger logger;
    private final LocationHierarchyService locationHierarchyService;
    private final AddressLevelTypeRepository addressLevelTypeRepository;

    @Autowired
    public LocationHierarchyController(LocationRepository locationRepository, LocationHierarchyService locationHierarchyService, OrganisationConfigService organisationConfigService, AddressLevelTypeRepository addressLevelTypeRepository) {
        this.locationRepository = locationRepository;
        this.locationHierarchyService = locationHierarchyService;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @GetMapping(value = "/locationHierarchy/search/lastModified")
    @ResponseBody
    public CollectionModel<EntityModel<AddressLevel>> getAddressLevels(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            Pageable pageable) {
        try {
            ArrayList<Long> addressLevelTypeIds = (ArrayList<Long>) locationHierarchyService.getLowestAddressLevelTypeHierarchiesForOrganisation();
            if (addressLevelTypeIds != null) {
                List<AddressLevelType> addressLevelTypes = addressLevelTypeRepository.findAllByIdIn(addressLevelTypeIds);
                return wrap(locationRepository.findByLastModifiedDateTimeAfterAndTypeIn(CHSEntity.toDate(lastModifiedDateTime), addressLevelTypes, pageable));
            }
        } catch (Exception exception) {
            logger.error("Error getting locationHierarchy", exception);
            return wrap(new PageImpl<>(Collections.emptyList()));
        }
        return wrap(new PageImpl<>(Collections.emptyList()));
    }
}
