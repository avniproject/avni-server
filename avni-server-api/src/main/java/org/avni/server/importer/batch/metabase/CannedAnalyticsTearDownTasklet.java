package org.avni.server.importer.batch.metabase;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.metabase.MetabaseDatabaseRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.importer.batch.AvniSpringBatchJobHelper;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.metabase.MetabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

@Component
@JobScope
public class CannedAnalyticsTearDownTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(CannedAnalyticsTearDownTasklet.class);

    private final OrganisationRepository organisationRepository;
    private final MetabaseService metabaseService;
    private final AvniSpringBatchJobHelper avniSpringBatchJobHelper;
    private final OrganisationConfigService organisationConfigService;
    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Autowired
    public CannedAnalyticsTearDownTasklet(OrganisationConfigService organisationConfigService, OrganisationRepository organisationRepository, MetabaseService metabaseService, AvniSpringBatchJobHelper avniSpringBatchJobHelper) {
        this.organisationConfigService = organisationConfigService;
        this.organisationRepository = organisationRepository;
        this.metabaseService = metabaseService;
        this.avniSpringBatchJobHelper = avniSpringBatchJobHelper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        avniSpringBatchJobHelper.authenticate(userId, organisationUUID);
        Organisation organisation = organisationRepository.findByUuid(organisationUUID);
        logger.info("Tearing down canned analytics for organisation {}", organisation.getName());
        try {
            CannedAnalyticsLockProvider.acquireLock(organisation);
            logger.info("Teardown job acquired Lock for organisation {}", organisation.getName());
            metabaseService.tearDownMetabase();
            if (organisationConfigService.isMetabaseSetupEnabled(organisation)) {
                organisationConfigService.setMetabaseSetupEnabled(organisation, false);
            }
            logger.info("Tear down completed for canned analytics for organisation {}", organisation.getName());
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_GATEWAY || e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                logger.error("502 Bad Gateway Error: ", e);
                throw new RuntimeException("Metabase is too busy. Please try later.", e);
            }
            throw e;
        } catch (Exception e) {
            logger.error("Error tearing down canned analytics for organisation {}", organisation.getName(), e);
            throw e;
        } finally {
            MetabaseDatabaseRepository.clearThreadLocalContext();
            CannedAnalyticsLockProvider.releaseLock(organisation);
        }
        return RepeatStatus.FINISHED;
    }
}
