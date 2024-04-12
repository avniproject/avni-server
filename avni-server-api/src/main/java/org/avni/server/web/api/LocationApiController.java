package org.avni.server.web.api;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.service.ConceptService;
import org.avni.server.web.response.LocationApiResponse;
import org.avni.server.web.response.ResponsePage;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
public class LocationApiController {
    private final LocationRepository locationRepository;
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;

    @Autowired
    public LocationApiController(LocationRepository locationRepository, ConceptRepository conceptRepository, ConceptService conceptService) {
        this.locationRepository = locationRepository;
        this.conceptRepository = conceptRepository;
        this.conceptService = conceptService;
    }

    @RequestMapping(value = "/api/locations", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponsePage getLocations(@RequestParam(value = "lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                    @RequestParam(value = "now", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                    Pageable pageable) {
        Page<AddressLevel> addresses = locationRepository.findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(lastModifiedDateTime, now, pageable);
        ArrayList<LocationApiResponse> locationApiResponses = new ArrayList<>();
        addresses.forEach(addressLevel -> {
            locationApiResponses.add(LocationApiResponse.fromAddressLevel(addressLevel, conceptRepository, conceptService));
        });
        return new ResponsePage(locationApiResponses, addresses.getNumberOfElements(), addresses.getTotalPages(), addresses.getSize());
    }

    @GetMapping(value = "/api/location/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<LocationApiResponse> get(@PathVariable("id") String legacyIdOrUuid) {
        AddressLevel addressLevel = locationRepository.findByLegacyIdOrUuid(legacyIdOrUuid);
        if (addressLevel == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(LocationApiResponse.fromAddressLevel(addressLevel, conceptRepository, conceptService));
    }
}
