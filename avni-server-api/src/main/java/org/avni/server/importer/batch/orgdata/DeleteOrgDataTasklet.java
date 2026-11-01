package org.avni.server.importer.batch.orgdata;

import org.avni.server.service.ImplementationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class DeleteOrgDataTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(DeleteOrgDataTasklet.class);

    private final ImplementationService implementationService;
    private final boolean deleteMetadata;
    private final boolean deleteAdminConfig;

    public DeleteOrgDataTasklet(ImplementationService implementationService,
                                @Value("#{jobParameters['deleteMetadata']}") String deleteMetadata,
                                @Value("#{jobParameters['deleteAdminConfig']}") String deleteAdminConfig) {
        this.implementationService = implementationService;
        this.deleteMetadata = Boolean.parseBoolean(deleteMetadata);
        this.deleteAdminConfig = Boolean.parseBoolean(deleteAdminConfig);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        logger.info("Deleting organisation data. deleteMetadata: {}, deleteAdminConfig: {}", deleteMetadata, deleteAdminConfig);
        implementationService.deleteImplementationData(deleteMetadata, deleteAdminConfig);
        return RepeatStatus.FINISHED;
    }
}
