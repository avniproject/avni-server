package org.avni.server.web;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.metabase.CannedAnalyticsBatchJobService;
import org.avni.server.service.metabase.CannedAnalyticsStatusService;
import org.avni.server.service.metabase.MetabaseService;
import org.avni.server.domain.metabase.CannedAnalyticsStatus;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web/metabase")
public class MetabaseController {
    private final MetabaseService metabaseService;
    private final AccessControlService accessControlService;
    private final OrganisationConfigService organisationConfigService;
    private final CannedAnalyticsStatusService cannedAnalyticsStatusService;
    private final CannedAnalyticsBatchJobService cannedAnalyticsBatchJobService;

    public MetabaseController(MetabaseService metabaseService, AccessControlService accessControlService, OrganisationConfigService organisationConfigService, CannedAnalyticsStatusService cannedAnalyticsStatusService, CannedAnalyticsBatchJobService cannedAnalyticsBatchJobService) {
        this.metabaseService = metabaseService;
        this.accessControlService = accessControlService;
        this.organisationConfigService = organisationConfigService;
        this.cannedAnalyticsStatusService = cannedAnalyticsStatusService;
        this.cannedAnalyticsBatchJobService = cannedAnalyticsBatchJobService;
    }

    @GetMapping("/status")
    public CannedAnalyticsStatus getStatus() {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        return cannedAnalyticsStatusService.getStatus(organisation);
    }

    @PostMapping("/teardown")
    public void tearDownMetabase() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        metabaseService.tearDownMetabase();
        cannedAnalyticsBatchJobService.createTearDownJob(organisation, UserContextHolder.getUserContext().getUser());
    }

    @PostMapping("/update-questions")
    public CannedAnalyticsStatus createQuestions() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        CannedAnalyticsStatus cannedAnalyticsStatus = cannedAnalyticsStatusService.getStatus(organisation);
        if (cannedAnalyticsStatus.isCreateQuestionAllowed()) {
            cannedAnalyticsBatchJobService.createCreateQuestionOnlyJob(organisation, UserContextHolder.getUserContext().getUser());
        }
        return cannedAnalyticsStatus;
    }

    @PostMapping("/setup")
    public void startSetupJob() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        organisationConfigService.assertReportingMetabaseSelfServiceEnableStatus(true);
        cannedAnalyticsBatchJobService.createSetupJob(UserContextHolder.getOrganisation(), UserContextHolder.getUserContext().getUser());
    }
}
