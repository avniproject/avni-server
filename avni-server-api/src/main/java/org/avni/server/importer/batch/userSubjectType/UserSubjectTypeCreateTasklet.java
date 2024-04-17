package org.avni.server.importer.batch.userSubjectType;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
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
    private final IndividualRepository individualRepository;
    private final UserRepository userRepository;
    private final UserSubjectRepository userSubjectRepository;
    private static final Logger logger = LoggerFactory.getLogger(UserSubjectTypeCreateTasklet.class);

    @Value("#{jobParameters['subjectTypeId']}")
    private Long subjectTypeId;

    @Autowired
    public UserSubjectTypeCreateTasklet(SubjectTypeRepository subjectTypeRepository,
                                        IndividualRepository individualRepository,
                                        UserRepository userRepository,
                                        UserSubjectRepository userSubjectRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualRepository = individualRepository;
        this.userRepository = userRepository;
        this.userSubjectRepository = userSubjectRepository;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        try {
            SubjectType subjectType = subjectTypeRepository.findOne(subjectTypeId);
            List<User> users = userRepository.findAllByIsVoidedFalseAndOrganisationId(subjectType.getOrganisationId());
            users.forEach(user -> {
                UserSubject userSubject = userSubjectRepository.findByUser(user);
                if (userSubject != null && !userSubject.isVoided()) return;

                Individual subject = new Individual();
                subject.setSubjectType(subjectType);
                subject.setFirstName(user.getName());
                subject.setOrganisationId(subjectType.getOrganisationId());
                subject.setRegistrationDate(user.getCreatedDateTime().toLocalDate());
                subject.assignUUID();
                individualRepository.save(subject);

                if (userSubject == null)
                    userSubject = new UserSubject();

                userSubject.setSubject(subject);
                userSubject.setUser(user);
                userSubject.assignUUID();
                userSubject.setOrganisationId(subjectType.getOrganisationId());

                userSubjectRepository.save(userSubject);
            });
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return RepeatStatus.FINISHED;
    }
}
