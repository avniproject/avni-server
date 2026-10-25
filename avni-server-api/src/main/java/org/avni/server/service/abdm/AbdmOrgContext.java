package org.avni.server.service.abdm;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

// DEMO-ONLY abdm-hackathon: never merged.
// Callbacks arrive with no Avni user, and the database picks its row-level-security role from the user's organisation
// (SetOrganisationJdbcInterceptor). This logs in as the configured organisation's system user for the work.
// DEMO-ONLY: one organisation per bridge, from abdm.organisation-uuid and abdm.system-user.
@Component
public class AbdmOrgContext {
    public void runAsOrg(Runnable work) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public Long organisationId() {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}
