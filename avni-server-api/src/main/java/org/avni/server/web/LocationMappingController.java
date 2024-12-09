package org.avni.server.web;

import org.avni.server.dao.LocationMappingRepository;
import org.avni.server.dao.LocationMappingSyncRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.ParentLocationMapping;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.springframework.hateoas.CollectionModel;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.EntityModel;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;

@RestController
public class LocationMappingController implements RestControllerResourceProcessor<ParentLocationMapping> {
    private final LocationMappingRepository locationMappingRepository;
    private final UserService userService;
    private final ScopeBasedSyncService<ParentLocationMapping> scopeBasedSyncService;
    private final LocationMappingSyncRepository locationMappingSyncRepository;

    @Autowired
    public LocationMappingController(UserService userService, LocationMappingRepository locationMappingRepository, ScopeBasedSyncService<ParentLocationMapping> scopeBasedSyncService, LocationMappingSyncRepository locationMappingSyncRepository) {
        this.userService = userService;
        this.locationMappingRepository = locationMappingRepository;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.locationMappingSyncRepository = locationMappingSyncRepository;
    }

    @RequestMapping(value = {"/locationMapping/search/lastModified", "/locationMapping/search/byCatchmentAndLastModified"}, method = RequestMethod.GET)
    @Transactional
    public CollectionModel<EntityModel<ParentLocationMapping>> getParentLocationMappingsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(scopeBasedSyncService.getSyncResultsByCatchment(locationMappingSyncRepository, userService.getCurrentUser(), lastModifiedDateTime, now, pageable, SyncEntityName.LocationMapping));
    }

    @Override
    public EntityModel<ParentLocationMapping> process(EntityModel<ParentLocationMapping> resource) {
        ParentLocationMapping content = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(content.getLocation().getUuid(), "locationUUID"));
        resource.add(Link.of(content.getParentLocation().getUuid(), "parentLocationUUID"));
        return resource;
    }

}
