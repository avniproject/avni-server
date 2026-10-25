package org.avni.server.service.abdm;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

// DEMO-ONLY abdm-hackathon: never merged.
// Callbacks arrive with no Avni user, and the database picks its row-level-security role from the user's organisation
// (SetOrganisationJdbcInterceptor). This logs in as the configured organisation's system user for the work.
// DEMO-ONLY: one organisation per bridge, from abdm.organisation-uuid and abdm.system-user.
// Whatever user the thread had before is put back afterwards, so calling this on a request thread is safe too.
@Component
public class AbdmOrgContext {
    private final AuthService authService;
    private final AbdmConfig cfg;
    private final OrganisationRepository organisationRepository;
    private final TransactionTemplate readOnly;

    @Autowired
    public AbdmOrgContext(AuthService authService, AbdmConfig cfg, OrganisationRepository organisationRepository,
                          PlatformTransactionManager transactionManager) {
        this.authService = authService;
        this.cfg = cfg;
        this.organisationRepository = organisationRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    public void runAsOrg(Runnable work) {
        if (!StringUtils.hasText(cfg.getSystemUser())) {
            throw new IllegalStateException("abdm.system-user (ABDM_SYSTEM_USER) is not set");
        }
        UserContext previousUser = UserContextHolder.getUserContext();
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            String organisationUuid = StringUtils.hasText(cfg.getOrganisationUuid()) ? cfg.getOrganisationUuid() : null;
            authService.authenticateByUserName(cfg.getSystemUser(), organisationUuid);
            work.run();
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
            if (previousUser != null) UserContextHolder.create(previousUser);
            if (previousAuthentication != null) SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }

    // The organisation the thread is acting for, else the configured one.
    public Long organisationId() {
        UserContext userContext = UserContextHolder.getUserContext();
        if (userContext != null && userContext.getOrganisationId() != null) return userContext.getOrganisationId();
        if (!StringUtils.hasText(cfg.getOrganisationUuid())) return null;
        Organisation organisation = organisationRepository.findByUuid(cfg.getOrganisationUuid());
        return organisation == null ? null : organisation.getId();
    }
}
