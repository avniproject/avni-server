package org.avni.server.importer.batch.userSubjectType;

import jakarta.transaction.Transactional;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class UserSubjectTypeCreateTasklet implements Tasklet {
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(UserSubjectTypeCreateTasklet.class);

    @Value("#{jobParameters['subjectTypeId']}")
    private Long subjectTypeId;

    @Autowired
    public UserSubjectTypeCreateTasklet(SubjectTypeRepository subjectTypeRepository,
                                        UserService userService) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.userService = userService;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        try {
            SubjectType subjectType = subjectTypeRepository.findOne(subjectTypeId);
            userService.ensureSubjectsForUserSubjectType(subjectType);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return RepeatStatus.FINISHED;
    }
}
