package org.avni.server.service.abdm;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

// Jobs and webhook events arrive with no Avni user, and the database picks its row-level-security role from the user's
// organisation (SetOrganisationJdbcInterceptor). runAs logs in as the organisation's system user, named in its Eka
// settings, inside that organisation, for the work; a missing or unusable one is an error, never a silent skip.
// Whatever user the thread had before is put back afterwards, so calling this on a request thread is safe too.
@Component
public class AbdmOrgContext {
    private final AuthService authService;
    private final OrganisationRepository organisationRepository;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final TransactionTemplate readOnly;

    // The organisation's system user is missing, unknown, an account administrator, or not a user of that organisation.
    public static class SystemUserException extends RuntimeException {
        public SystemUserException(AbdmOrganisation organisation, String problem, Throwable cause) {
            super("ABDM work for " + organisation + " not run: " + problem, cause);
        }
    }

    @Autowired
    public AbdmOrgContext(AuthService authService, OrganisationRepository organisationRepository,
                          ExternalSystemConfigRepository externalSystemConfigRepository, PlatformTransactionManager transactionManager) {
        this.authService = authService;
        this.organisationRepository = organisationRepository;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    // The organisation and the system user its Eka settings name; a null system user when there are none.
    public AbdmOrganisation organisation(long organisationId) {
        Organisation organisation = organisationRepository.findOne(organisationId);
        String systemUser;
        try {
            systemUser = externalSystemConfigRepository.getEkaSystemConfig(organisationId).getAvniSystemUser();
        } catch (EkaNotConfiguredException e) {
            systemUser = null;
        }
        return new AbdmOrganisation(organisationId, organisation == null ? null : organisation.getName(),
                StringUtils.hasText(systemUser) ? systemUser.trim() : null);
    }

    public void runAs(AbdmOrganisation organisation, Runnable work) {
        if (!StringUtils.hasText(organisation.systemUser())) {
            throw new SystemUserException(organisation, "its Eka settings name no system user (avniSystemUser)", null);
        }
        Organisation onRecord = organisationRepository.findOne(organisation.id());
        if (onRecord == null || !StringUtils.hasText(onRecord.getUuid())) {
            throw new SystemUserException(organisation, "the organisation is not found", null);
        }
        UserContext previousUser = UserContextHolder.getUserContext();
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            UserContext context;
            try {
                // Named, so the row filter is the organisation's: an account administrator with none named would have it off.
                context = authService.authenticateByUserName(organisation.systemUser(), onRecord.getUuid());
            } catch (RuntimeException e) {
                throw new SystemUserException(organisation, "its system user could not log in", e);
            }
            User user = context == null ? null : context.getUser();
            if (user != null && user.isAdmin()) {
                throw new SystemUserException(organisation, "its system user is an account administrator", null);
            }
            Long actingFor = context == null ? null : context.getOrganisationId();
            // A login naming the organisation acts for it whoever logs in, so the user's own organisation decides.
            boolean usable = user != null && !Boolean.TRUE.equals(user.isVoided()) && actingFor != null && actingFor == organisation.id()
                    && user.getOrganisationId() != null && user.getOrganisationId() == organisation.id();
            if (!usable) throw new SystemUserException(organisation, "its system user is not an active user of that organisation", null);
            work.run();
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
            if (previousUser != null) UserContextHolder.create(previousUser);
            if (previousAuthentication != null) SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        }
    }

    // No user, so connections take no organisation's role: for a duty the system user cannot do, each statement naming its
    // organisation. Outside a transaction only (the worker, the job runner); the thread's user is put back.
    public <T> T withoutLogin(Supplier<T> work) {
        UserContext previousUser = UserContextHolder.getUserContext();
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        UserContextHolder.clear();
        SecurityContextHolder.clearContext();
        try {
            return work.get();
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

    // The organisation of the user the thread acts as; null when there is none.
    public Long organisationId() {
        UserContext userContext = UserContextHolder.getUserContext();
        return userContext == null ? null : userContext.getOrganisationId();
    }
}
