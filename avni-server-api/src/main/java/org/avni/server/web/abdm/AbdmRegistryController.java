package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.registry.AbdmRegistryService;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// DEMO-ONLY abdm-hackathon: never merged.
// M4 registries for an organisation admin: search facilities (HFR) and professionals (HPR), submit a facility.
// The registry's answer, a refusal included, comes back as 200 with {mode, results} or {mode, error, status, abdmCode}.
@RestController
public class AbdmRegistryController {
    private final AbdmRegistryService registryService;
    private final AccessControlService accessControlService;

    @Autowired
    public AbdmRegistryController(AbdmRegistryService registryService, AccessControlService accessControlService) {
        this.registryService = registryService;
        this.accessControlService = accessControlService;
    }

    @PostMapping("/web/abdm/m4/facilities/search")
    public ResponseEntity<JsonNode> searchFacilities(@RequestBody(required = false) JsonNode criteria) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return ResponseEntity.ok(registryService.searchFacilities(orEmpty(criteria)));
    }

    @PostMapping("/web/abdm/m4/professionals/search")
    public ResponseEntity<JsonNode> searchProfessionals(@RequestBody(required = false) JsonNode criteria) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return ResponseEntity.ok(registryService.searchProfessionals(orEmpty(criteria)));
    }

    @PostMapping("/web/abdm/m4/facilities/submit")
    public ResponseEntity<JsonNode> submitFacility(@RequestBody(required = false) JsonNode facility) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return ResponseEntity.ok(registryService.submitFacility(orEmpty(facility)));
    }

    private static JsonNode orEmpty(JsonNode body) {
        return body == null || body.isNull() ? AbdmJson.obj() : body;
    }
}
