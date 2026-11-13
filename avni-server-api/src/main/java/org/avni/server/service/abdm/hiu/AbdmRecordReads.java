package org.avni.server.service.abdm.hiu;

import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;

// Who read the records received under a consent request, and when (abdm_record_read, V1_421). Written before the records
// are read, so a read that cannot be logged does not happen. Kept after the records are erased; delete data clears it.
@Component
public class AbdmRecordReads {
    private final NamedParameterJdbcTemplate jdbc;
    private final AbdmOrgContext orgContext;

    public AbdmRecordReads(NamedParameterJdbcTemplate jdbc, AbdmOrgContext orgContext) {
        this.jdbc = jdbc;
        this.orgContext = orgContext;
    }

    public void logged(String consentRequestUuid) {
        User user = UserContextHolder.getUser();
        Long organisationId = orgContext.organisationId();
        if (user == null || user.getId() == null || organisationId == null) {
            throw new IllegalStateException("A read of received ABDM records needs a user of an organisation");
        }
        jdbc.update("insert into abdm_record_read (organisation_id, user_id, consent_request_uuid) values (:org, :user, :request)",
                new MapSqlParameterSource("org", organisationId).addValue("user", user.getId())
                        .addValue("request", consentRequestUuid, Types.VARCHAR));
    }
}
