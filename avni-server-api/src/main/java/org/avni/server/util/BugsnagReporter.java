package org.avni.server.util;

import com.bugsnag.Bugsnag;
import com.bugsnag.Report;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BugsnagReporter {
    private static final Logger logger = LoggerFactory.getLogger(BugsnagReporter.class);
    private final Bugsnag bugsnag;

    @Autowired
    public BugsnagReporter(Bugsnag bugsnag) {
        this.bugsnag = bugsnag;
    }

    public void logAndReportToBugsnag(Throwable e) {
        reportToBugsnag(e);
        log(e);
    }

    private void log(Throwable e) {
        logger.error(e.getMessage(), e);
    }

    private void reportToBugsnag(Throwable e) {
        UserContext userContext = UserContextHolder.getUserContext();
        String username = userContext.getUserName();
        String organisationName = userContext.getOrganisationName();
        Report report = bugsnag.buildReport(e).setUser(username, organisationName, username);
        bugsnag.notify(report);
    }
}
