package org.avni.server.service.abha;

import org.avni.server.domain.User;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;

// A user who passed every ABHA check, with what the checks found.
public class AbhaAccess {
    private final User user;
    private final Long organisationId;
    private final EkaSystemConfig ekaSystemConfig;
    private final String hipId;

    public AbhaAccess(User user, Long organisationId, EkaSystemConfig ekaSystemConfig, String hipId) {
        this.user = user;
        this.organisationId = organisationId;
        this.ekaSystemConfig = ekaSystemConfig;
        this.hipId = hipId;
    }

    public User getUser() {
        return user;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public EkaSystemConfig getEkaSystemConfig() {
        return ekaSystemConfig;
    }

    public String getHipId() {
        return hipId;
    }
}
