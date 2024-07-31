package org.avni.server.importer.batch.userSubjectType;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
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

import javax.transaction.Transactional;
import java.util.List;

@Component
@JobScope
public class UserSubjectTypeCreateTasklet implements Tasklet {
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(UserSubjectTypeCreateTasklet.class);

    @Value("#{jobParameters['subjectTypeId']}")
    private Long subjectTypeId;

    @Autowired
    public UserSubjectTypeCreateTasklet(SubjectTypeRepository subjectTypeRepository,
                                        UserRepository userRepository,
                                        UserService userService) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        try {
            SubjectType subjectType = subjectTypeRepository.findOne(subjectTypeId);
            List<User> users = userRepository.findAllByIsVoidedFalseAndOrganisationId(subjectType.getOrganisationId());
            users.forEach(user -> {
                userService.ensureSubjectForUser(user, subjectType);
            });
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return RepeatStatus.FINISHED;
    }
}
