package org.avni.server.service.abdm;

import com.bugsnag.Bugsnag;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.framework.security.AuthService;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Runs an ABDM job for each organisation with record sharing on (AbdmAccess), as that organisation's system user, one
// after another, as MessageSenderJob does for messaging. A failure, a missing system user included, is logged with its
// stack trace, sent to Bugsnag and kept for the status page (AbdmJobRuns); the next organisation still runs.
// The run has a time budget; each organisation is given its share of what is left, as a deadline the work keeps to.
@Component
public class AbdmJobRunner {
    private static final Logger logger = LoggerFactory.getLogger(AbdmJobRunner.class);

    private final AuthService authService;
    private final OrganisationConfigService organisationConfigService;
    private final AbdmAccess access;
    private final AbdmOrgContext orgContext;
    private final AbdmJobRuns jobRuns;
    private final Bugsnag bugsnag;
    private final Clock clock;

    @Autowired
    public AbdmJobRunner(AuthService authService, OrganisationConfigService organisationConfigService, AbdmAccess access,
                         AbdmOrgContext orgContext, AbdmJobRuns jobRuns, Bugsnag bugsnag) {
        this(authService, organisationConfigService, access, orgContext, jobRuns, bugsnag, Clock.systemUTC());
    }

    AbdmJobRunner(AuthService authService, OrganisationConfigService organisationConfigService, AbdmAccess access,
                  AbdmOrgContext orgContext, AbdmJobRuns jobRuns, Bugsnag bugsnag, Clock clock) {
        this.authService = authService;
        this.organisationConfigService = organisationConfigService;
        this.access = access;
        this.orgContext = orgContext;
        this.jobRuns = jobRuns;
        this.bugsnag = bugsnag;
        this.clock = clock;
    }

    public void forEachOrganisation(String task, Duration budget, Consumer<Instant> work) {
        Instant runDeadline = clock.instant().plus(budget);
        try {
            List<AbdmOrganisation> organisations;
            try {
                organisations = sharingOrganisations();
            } catch (RuntimeException e) {
                logger.error("ABDM {} not run: the organisations sharing records could not be read", task, e);
                bugsnag.notify(e);
                return;
            }
            for (int i = 0; i < organisations.size(); i++) {
                AbdmOrganisation organisation = organisations.get(i);
                Instant now = clock.instant();
                Duration left = Duration.between(now, runDeadline);
                Instant deadline = left.isNegative() ? now : now.plus(left.dividedBy(organisations.size() - i));
                runFor(organisation, task, deadline, work);
            }
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void runFor(AbdmOrganisation organisation, String task, Instant deadline, Consumer<Instant> work) {
        try {
            orgContext.runAs(organisation, () -> work.accept(deadline));
            jobRuns.succeeded(organisation.id(), task, clock.instant());
        } catch (RuntimeException e) {
            logger.error("ABDM {} failed for {}", task, organisation, e);
            bugsnag.notify(e);
            try {
                jobRuns.failed(organisation.id(), task, e.getClass().getSimpleName() + ": " + e.getMessage(), clock.instant());
            } catch (RuntimeException recordFailure) {
                logger.error("ABDM {}: the failure for {} could not be kept for the status page", task, organisation, recordFailure);
            }
        }
    }

    // Read as the admin user, as MessageSenderJob does: the switch first, then the rest of what AbdmAccess needs.
    private List<AbdmOrganisation> sharingOrganisations() {
        authService.authenticateByUserName("admin", null);
        List<AbdmOrganisation> organisations = new ArrayList<>();
        for (OrganisationConfig config : organisationConfigService.findAllWithFeatureEnabled(OrganisationConfigSettingKey.enableAbdmRecordSharing.name())) {
            Long organisationId = config.getOrganisationId();
            if (organisationId != null && access.recordSharingOn(organisationId)) organisations.add(orgContext.organisation(organisationId));
        }
        return organisations;
    }
}
