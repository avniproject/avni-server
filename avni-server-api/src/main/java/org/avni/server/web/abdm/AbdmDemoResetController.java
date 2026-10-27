package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// "Reset demo data" on the ABDM page: clears this organisation's ABDM tables (traffic, consents, records received,
// links, webhook events taken and set aside) so a demo starts clean. Patients and visits are untouched; their visits
// can be linked again.
@RestController
public class AbdmDemoResetController {
    // Children first, so no row is left pointing at a deleted one.
    private static final List<String> TABLES = List.of("abdm_health_information", "abdm_data_flow", "abdm_consent",
            "abdm_link_request", "abdm_care_context", "abdm_parked_event", "abdm_inbound_event", "abdm_message");

    private final AccessControlService accessControlService;
    private final NamedParameterJdbcTemplate jdbc;
    private final AbdmOrgContext orgContext;

    public AbdmDemoResetController(AccessControlService accessControlService, NamedParameterJdbcTemplate jdbc, AbdmOrgContext orgContext) {
        this.accessControlService = accessControlService;
        this.jdbc = jdbc;
        this.orgContext = orgContext;
    }

    @PostMapping("/web/abdm/demo/reset")
    public Map<String, Object> reset() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        MapSqlParameterSource org = new MapSqlParameterSource("org", orgContext.organisationId());
        int deleted = 0;
        for (String table : TABLES) {
            deleted += jdbc.update("delete from " + table + " where organisation_id = :org", org);
        }
        return Map.of("rowsDeleted", deleted);
    }
}
