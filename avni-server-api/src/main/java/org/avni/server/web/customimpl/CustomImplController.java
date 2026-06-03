package org.avni.server.web.customimpl;

import org.avni.server.service.customimpl.CustomImplService;
import org.avni.server.web.request.customimpl.EncounterSearchRequest;
import org.avni.server.web.response.ResponsePage;
import org.avni.server.web.response.customimpl.CatchmentLocationsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/impl")
public class CustomImplController {

    private final CustomImplService service;

    @Autowired
    public CustomImplController(CustomImplService service) {
        this.service = service;
    }

    @GetMapping("/catchmentLocations")
    @Transactional(readOnly = true)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<CatchmentLocationsResponse> getCatchmentLocations() {
        return ResponseEntity.ok(service.getCatchmentLocationsForCurrentUser());
    }

    @GetMapping("/encountersWithLocation")
    @Transactional(readOnly = true)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<ResponsePage> getEncountersWithLocation(
            @RequestParam("encounterType") String encounterType,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "locationUuid", required = false) String locationUuid,
            @RequestParam(value = "linkedEncounterType", required = false) String linkedEncounterType,
            @RequestParam(value = "linkedObservationConceptUuid", required = false) String linkedObservationConceptUuid,
            @RequestParam(value = "linkedLocationUuid", required = false) String linkedLocationUuid,
            @PageableDefault(size = 50) Pageable pageable) {
        EncounterSearchRequest request = new EncounterSearchRequest(
                encounterType,
                status,
                locationUuid,
                linkedEncounterType,
                linkedObservationConceptUuid,
                linkedLocationUuid);
        return ResponseEntity.ok(service.findEncountersWithLocation(request, pageable));
    }
}
