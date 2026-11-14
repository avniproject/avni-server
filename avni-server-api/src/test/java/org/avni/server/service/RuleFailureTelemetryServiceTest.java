package org.avni.server.service;

import org.avni.server.dao.RuleFailureTelemetryRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.Program;
import org.avni.server.domain.RuleFailureTelemetry;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.enums.ruleFailure.AppType;
import org.avni.server.domain.enums.ruleFailure.EntityType;
import org.avni.server.domain.enums.ruleFailure.SourceType;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.request.rules.response.RuleError;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuleFailureTelemetryServiceTest {
    private final RuleFailureTelemetryRepository repository = mock(RuleFailureTelemetryRepository.class);
    private final RuleFailureTelemetryService service = new RuleFailureTelemetryService(repository);
    private final Program program = new ProgramBuilder().withUuid("program-uuid").build();
    private final Individual subject = new SubjectBuilder().withUUID("subject-uuid").build();
    private final User user = new UserBuilder().id(7).userName("dinesh@dinesh").build();
    private final RuleError error = new RuleError();

    @Before
    public void setUp() {
        Organisation organisation = new TestOrganisationBuilder().setId(2).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        userContext.setUser(user);
        UserContextHolder.create(userContext);
        error.setMessage("QA1079 eligibility rule throws");
        error.setStack("Error: QA1079 eligibility rule throws");
        when(repository.tryLockFailureUntilTransactionEnds(any(), any(), any())).thenReturn(true);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void recordsTheFailureAgainstTheProgramAndSubjectSoTheOrganisationSeesIt() {
        service.recordEligibilityRuleFailure(subject, program.getUuid(), SourceType.EnrolmentEligibilityCheck, error);

        ArgumentCaptor<RuleFailureTelemetry> saved = ArgumentCaptor.forClass(RuleFailureTelemetry.class);
        verify(repository).save(saved.capture());
        RuleFailureTelemetry telemetry = saved.getValue();
        assertEquals("program-uuid", telemetry.getRuleUuid());
        assertEquals("program-uuid", telemetry.getSourceId());
        assertEquals(SourceType.EnrolmentEligibilityCheck, telemetry.getSourceType());
        assertEquals("subject-uuid", telemetry.getIndividualUuid());
        assertEquals("subject-uuid", telemetry.getEntityId());
        assertEquals(EntityType.Individual, telemetry.getEntityType());
        assertEquals(AppType.Web, telemetry.getAppType());
        assertEquals("QA1079 eligibility rule throws", telemetry.getErrorMessage());
        assertEquals("Error: QA1079 eligibility rule throws", telemetry.getStacktrace());
        assertEquals(user, telemetry.getUser());
        assertEquals(Long.valueOf(2), telemetry.getOrganisationId());
        assertFalse(telemetry.getClosed());
    }

    @Test
    public void doesNotRecordTheSameOpenFailureAgainEachTimeTheProfileIsOpened() {
        when(repository.hasOpenFailure("program-uuid", "subject-uuid", "QA1079 eligibility rule throws")).thenReturn(true);

        service.recordEligibilityRuleFailure(subject, program.getUuid(), SourceType.EnrolmentEligibilityCheck, error);

        verify(repository, never()).save(any());
    }

    @Test
    public void locksTheRuleSubjectAndMessageBeforeCheckingForAnOpenFailure_soCallsAtTheSameTimeSaveOneRow() {
        service.recordEligibilityRuleFailure(subject, program.getUuid(), SourceType.EnrolmentEligibilityCheck, error);

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).tryLockFailureUntilTransactionEnds("program-uuid", "subject-uuid", "QA1079 eligibility rule throws");
        inOrder.verify(repository).hasOpenFailure("program-uuid", "subject-uuid", "QA1079 eligibility rule throws");
        inOrder.verify(repository).save(any());
    }

    @Test
    public void doesNotWaitOrSaveWhenAnotherCallIsAlreadySavingTheSameFailure() {
        when(repository.tryLockFailureUntilTransactionEnds("program-uuid", "subject-uuid", "QA1079 eligibility rule throws")).thenReturn(false);

        service.recordEligibilityRuleFailure(subject, program.getUuid(), SourceType.EnrolmentEligibilityCheck, error);

        verify(repository, never()).hasOpenFailure(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    public void aFailureWithoutAMessageIsSavedWithAFixedMessage_soItCanBeStoredAndMatchedNextTime() {
        RuleError withoutMessage = new RuleError();

        service.recordEligibilityRuleFailure(subject, program.getUuid(), SourceType.EnrolmentEligibilityCheck, withoutMessage);

        verify(repository).tryLockFailureUntilTransactionEnds("program-uuid", "subject-uuid", RuleFailureTelemetryService.MESSAGE_WHEN_RULE_GAVE_NONE);
        verify(repository).hasOpenFailure("program-uuid", "subject-uuid", RuleFailureTelemetryService.MESSAGE_WHEN_RULE_GAVE_NONE);
        ArgumentCaptor<RuleFailureTelemetry> saved = ArgumentCaptor.forClass(RuleFailureTelemetry.class);
        verify(repository).save(saved.capture());
        assertEquals(RuleFailureTelemetryService.MESSAGE_WHEN_RULE_GAVE_NONE, saved.getValue().getErrorMessage());
        assertEquals(RuleFailureTelemetryService.MESSAGE_WHEN_RULE_GAVE_NONE, saved.getValue().getStacktrace());
    }
}
