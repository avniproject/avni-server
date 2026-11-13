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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

// Runs an ABDM job for each organisation with record sharing on (AbdmAccess), or for each one a selection names, as that
// organisation's system user, one after another, as MessageSenderJob does for messaging. A failure, a system user that
// cannot act included, is logged with its stack trace and kept for the status page (AbdmJobRuns), and sent to Bugsnag
// on the first after a success and then hourly; the next organisation still runs. An Eka setting missing where both
// switches are on is kept for the status page, named.
// The run has a time budget; each organisation is given its share of what is left, as a deadline the work keeps to, and
// each run starts one organisation further on. A run told to stop (the server shutting down) takes no further
// organisation and reports nothing for the one it was in.
@Component
public class AbdmJobRunner {
    private static final Logger logger = LoggerFactory.getLogger(AbdmJobRunner.class);

    private final AuthService authService;
    private final OrganisationConfigService organisationConfigService;
    private final AbdmAccess access;
    private final AbdmOrgContext orgContext;
    private final AbdmJobRuns jobRuns;
    private final Bugsnag bugsnag;
    private final AbdmScheduler scheduler;
    private final Clock clock;
    // Runs so far of each task: each run starts one organisation further on, so no organisation is always last.
    private final Map<String, AtomicInteger> runs = new ConcurrentHashMap<>();

    @Autowired
    public AbdmJobRunner(AuthService authService, OrganisationConfigService organisationConfigService, AbdmAccess access,
                         AbdmOrgContext orgContext, AbdmJobRuns jobRuns, Bugsnag bugsnag, AbdmScheduler scheduler) {
        this(authService, organisationConfigService, access, orgContext, jobRuns, bugsnag, scheduler, Clock.systemUTC());
    }

    AbdmJobRunner(AuthService authService, OrganisationConfigService organisationConfigService, AbdmAccess access,
                  AbdmOrgContext orgContext, AbdmJobRuns jobRuns, Bugsnag bugsnag, AbdmScheduler scheduler, Clock clock) {
        this.authService = authService;
        this.organisationConfigService = organisationConfigService;
        this.access = access;
        this.orgContext = orgContext;
        this.jobRuns = jobRuns;
        this.bugsnag = bugsnag;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    // For each organisation with record sharing on: work that calls Eka or links or serves records. One with both
    // switches on but an Eka setting missing is recorded as failed, naming it.
    public void forEachOrganisation(String task, Duration budget, Consumer<Instant> work) {
        forOrganisations(task, budget, () -> sharingOrganisationIds(task), work);
    }

    // For each organisation the selection names, whatever its switches: a duty such as erasing records when their consent
    // ends does not lapse when record sharing is switched off. The selection is read as the admin user, across organisations.
    public void forOrganisations(String task, Duration budget, Supplier<List<Long>> selection, Consumer<Instant> work) {
        forOrganisations(task, budget, selection, work, null);
    }

    // As above; where the system user cannot act, withoutLogin runs for the organisation with no user, and it is still reported.
    public void forOrganisations(String task, Duration budget, Supplier<List<Long>> selection, Consumer<Instant> work,
                                 LongConsumer withoutLogin) {
        Instant runDeadline = clock.instant().plus(budget);
        try {
            List<Long> organisationIds;
            try {
                authService.authenticateByUserName("admin", null);
                organisationIds = selection.get();
            } catch (RuntimeException e) {
                logger.error("ABDM {} not run: its organisations could not be read", task, e);
                bugsnag.notify(e);
                return;
            }
            int count = organisationIds.size();
            int start = count == 0 ? 0 : Math.floorMod(runs.computeIfAbsent(task, t -> new AtomicInteger()).getAndIncrement(), count);
            for (int i = 0; i < count; i++) {
                if (toldToStop()) {
                    logger.info("ABDM {} told to stop; {} organisation(s) left for the next run", task, count - i);
                    return;
                }
                Instant now = clock.instant();
                Duration left = Duration.between(now, runDeadline);
                Instant deadline = left.isNegative() ? now : now.plus(left.dividedBy(count - i));
                runFor(organisationIds.get((start + i) % count), task, deadline, work, withoutLogin);
            }
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void runFor(long organisationId, String task, Instant deadline, Consumer<Instant> work, LongConsumer withoutLogin) {
        AbdmOrganisation organisation = new AbdmOrganisation(organisationId, null, null);
        try {
            organisation = orgContext.organisation(organisationId);
            try {
                orgContext.runAs(organisation, () -> work.accept(deadline));
            } catch (AbdmOrgContext.SystemUserException e) {
                if (withoutLogin == null) throw e;
                try {
                    orgContext.withoutLogin(() -> {
                        withoutLogin.accept(organisationId);
                        return null;
                    });
                } catch (RuntimeException failure) {
                    // Both need fixing: the work failed, and the system user it would have run as cannot act. Bugsnag
                    // does not send suppressed exceptions, so the system user's goes on its own.
                    failure.addSuppressed(e);
                    report(organisationId, task, organisation, failure, "; with no login, as " + e.getMessage(), e);
                    return;
                }
                report(organisationId, task, organisation, e, "; done without it, with no login");
                return;
            }
            jobRuns.succeeded(organisationId, task, clock.instant());
        } catch (RuntimeException e) {
            if (toldToStop()) {
                // The server is stopping: the failure is the stop's, not the organisation's; what is left waits for the next run.
                logger.info("ABDM {} for {} stopped: told to stop ({})", task, organisation, e.getClass().getSimpleName());
                return;
            }
            report(organisationId, task, organisation, e, "");
        }
    }

    // Every failure is kept for the status page. Bugsnag hears of the first after a success, then at most once an hour
    // while the organisation's run keeps failing (AbdmJobRuns.failed), so an outage is one alarm an hour, not one a run.
    // The exceptions in `also` go with it, as Bugsnag drops suppressed ones.
    private void report(long organisationId, String task, AbdmOrganisation organisation, RuntimeException e, String outcome, Throwable... also) {
        logger.error("ABDM {} failed for {}{}", task, organisation, outcome, e);
        boolean alarm = true;
        try {
            alarm = jobRuns.failed(organisationId, task, e.getClass().getSimpleName() + ": " + e.getMessage() + outcome, clock.instant());
        } catch (RuntimeException recordFailure) {
            logger.error("ABDM {}: the failure for {} could not be kept for the status page", task, organisation, recordFailure);
        }
        if (!alarm) return;
        bugsnag.notify(e);
        for (Throwable t : also) bugsnag.notify(t);
    }

    // The scheduler stopping, seen even when a pool has cleared the thread's interrupt, or the thread interrupted.
    private boolean toldToStop() {
        return scheduler.toldToStop() || Thread.currentThread().isInterrupted();
    }

    // As MessageSenderJob does: the switch first, then the rest of what AbdmAccess needs.
    private List<Long> sharingOrganisationIds(String task) {
        List<Long> organisationIds = new ArrayList<>();
        for (OrganisationConfig config : organisationConfigService.findAllWithFeatureEnabled(OrganisationConfigSettingKey.enableAbdmRecordSharing.name())) {
            Long organisationId = config.getOrganisationId();
            if (organisationId == null) continue;
            AbdmAccess.Sharing sharing = access.sharing(organisationId);
            if (sharing.on()) organisationIds.add(organisationId);
            else if (sharing.switchedOn()) notSharing(organisationId, task, sharing.missing());
        }
        return organisationIds;
    }

    // Both switches on, but an Eka setting missing: the work cannot run, and the status page says which setting. Not sent to
    // Bugsnag: the fix is in the organisation's settings, which the page names, and it would alarm every run.
    private void notSharing(long organisationId, String task, String missing) {
        logger.warn("ABDM {} not run for organisation {}: {}", task, organisationId, missing);
        try {
            jobRuns.failed(organisationId, task, missing, clock.instant());
        } catch (RuntimeException e) {
            logger.error("ABDM {}: the missing settings of organisation {} could not be kept for the status page", task, organisationId, e);
        }
    }
}
