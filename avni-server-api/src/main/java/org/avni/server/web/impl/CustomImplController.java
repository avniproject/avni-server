package org.avni.server.web.impl;

import org.avni.server.service.impl.CustomImplService;
import org.avni.server.service.impl.EncounterStatus;
import org.avni.server.web.response.ResponsePage;
import org.avni.server.web.response.impl.CatchmentLocationsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Custom Implementation APIs.
 *
 * Generic endpoints that don't fit the per-entity CRUD pattern but stay
 * implementation-agnostic. Tanuh's physician webapp is the first consumer;
 * other implementations are expected to plug in without server changes.
 */
@RestController
@RequestMapping("/api/impl")
public class CustomImplController {

    private final CustomImplService service;

    @Autowired
    public CustomImplController(CustomImplService service) {
        this.service = service;
    }

    /**
     * The signed-in user's catchment as a hierarchy of AddressLevels.
     * Drives location-filter dropdowns on the client.
     */
    @GetMapping("/catchmentLocations")
    @Transactional(readOnly = true)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<CatchmentLocationsResponse> getCatchmentLocations() {
        return ResponseEntity.ok(service.getCatchmentLocationsForCurrentUser());
    }

    /**
     * Paged encounters joined with the subject's location hierarchy.
     * Optionally filtered to encounters whose subject lives in the
     * AddressLevel subtree rooted at {@code locationUuid}.
     */
    @GetMapping("/encountersWithLocation")
    @Transactional(readOnly = true)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<ResponsePage> getEncountersWithLocation(
            @RequestParam("encounterType") String encounterType,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "locationUuid", required = false) String locationUuid,
            @PageableDefault(size = 50) Pageable pageable) {
        EncounterStatus parsedStatus;
        try {
            parsedStatus = EncounterStatus.from(status);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok(
                service.findEncountersWithLocation(encounterType, parsedStatus, locationUuid, pageable));
    }
}
