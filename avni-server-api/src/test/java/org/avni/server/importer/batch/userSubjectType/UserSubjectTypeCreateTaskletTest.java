package org.avni.server.importer.batch.userSubjectType;

import org.avni.server.application.Subject;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.UserService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class UserSubjectTypeCreateTaskletTest {

    @Mock
    private SubjectTypeRepository subjectTypeRepository;

    @Mock
    private UserService userService;

    private UserSubjectTypeCreateTasklet tasklet;

    private static final Long SUBJECT_TYPE_ID = 42L;

    @Before
    public void setup() {
        openMocks(this);
        tasklet = new UserSubjectTypeCreateTasklet(subjectTypeRepository, userService);
        ReflectionTestUtils.setField(tasklet, "subjectTypeId", SUBJECT_TYPE_ID);
    }

    @Test
    public void execute_shouldEnsureSubjects_whenSubjectTypeExistsAndIsNotVoided() {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setType(Subject.User)
                .build();
        subjectType.setVoided(false);
        when(subjectTypeRepository.findOne(SUBJECT_TYPE_ID)).thenReturn(subjectType);

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        assertEquals(RepeatStatus.FINISHED, result);
        verify(userService).ensureSubjectsForUserSubjectType(subjectType);
    }

    @Test
    public void execute_shouldSkip_whenSubjectTypeIsVoided() {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setType(Subject.User)
                .build();
        subjectType.setVoided(true);
        when(subjectTypeRepository.findOne(SUBJECT_TYPE_ID)).thenReturn(subjectType);

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        assertEquals(RepeatStatus.FINISHED, result);
        verify(userService, never()).ensureSubjectsForUserSubjectType(subjectType);
    }

    @Test
    public void execute_shouldSkip_whenSubjectTypeNotFound() {
        when(subjectTypeRepository.findOne(SUBJECT_TYPE_ID)).thenReturn(null);

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        assertEquals(RepeatStatus.FINISHED, result);
        verify(userService, never()).ensureSubjectsForUserSubjectType(org.mockito.ArgumentMatchers.any());
    }
}
