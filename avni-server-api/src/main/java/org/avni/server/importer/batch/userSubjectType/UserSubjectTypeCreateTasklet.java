package org.avni.server.importer.batch.userSubjectType;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.projection.UserWebProjection;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@JobScope
public class UserSubjectTypeCreateTasklet implements Tasklet {
    private final SubjectTypeRepository subjectTypeRepository;
    private final IndividualRepository individualRepository;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final UserSubjectRepository userSubjectRepository;

    @Value("#{jobParameters['subjectTypeId']}")
    private Long subjectTypeId;

    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Autowired
    public UserSubjectTypeCreateTasklet(SubjectTypeRepository subjectTypeRepository,
                                        IndividualRepository individualRepository,
                                        UserRepository userRepository,
                                        OrganisationRepository organisationRepository, UserSubjectRepository userSubjectRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualRepository = individualRepository;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.userSubjectRepository = userSubjectRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        SubjectType subjectType = subjectTypeRepository.findOne(subjectTypeId);
        Organisation organisation = organisationRepository.findByUuid(organisationUUID);
        List<User> users = userRepository.findAllByIsVoidedFalseAndOrganisationId(subjectTypeId);
        users.forEach(user -> {
            UserSubject userSubject = userSubjectRepository.findByUser(user);
            if (userSubject != null && !userSubject.isVoided()) return;

            Individual subject = new Individual();
            subject.setSubjectType(subjectType);
            subject.setFirstName(user.getName());
            subject.setOrganisationId(subjectType.getOrganisationId());
            individualRepository.save(subject);

            if (userSubject == null)
                userSubject = new UserSubject();

            userSubject.setSubject(subject);
            userSubject.setUser(user);
            userSubject.assignUUID();
            userSubject.setOrganisationId(organisation.getId());

            userSubjectRepository.save(userSubject);
        });
        return RepeatStatus.FINISHED;
    }
}
