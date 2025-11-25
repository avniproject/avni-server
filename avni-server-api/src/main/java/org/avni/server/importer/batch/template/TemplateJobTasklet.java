package org.avni.server.importer.batch.template;


import org.avni.server.service.ImplementationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class TemplateJobTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(TemplateJobTasklet.class);

    private final ImplementationService implementationService;

    @Autowired
    public TemplateJobTasklet(ImplementationService implementationService) {
        this.implementationService = implementationService;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        implementationService.deleteImplementationData(true, true);
        return RepeatStatus.FINISHED;
    }
}
