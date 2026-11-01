package org.avni.server.importer.batch.orgdata;

import org.avni.server.service.ImplementationService;
import org.avni.server.web.validation.ValidationException;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.repeat.RepeatStatus;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DeleteOrgDataJobUnitTest {
    @Test
    public void taskletDeletesWithTheFlagsTheJobWasStartedWith() throws Exception {
        ImplementationService implementationService = mock(ImplementationService.class);

        RepeatStatus repeatStatus = new DeleteOrgDataTasklet(implementationService, "true", "false").execute(null, null);

        assertEquals(RepeatStatus.FINISHED, repeatStatus);
        verify(implementationService).deleteImplementationData(true, false);
    }

    @Test
    public void taskletPassesBothFlagsThrough() throws Exception {
        ImplementationService implementationService = mock(ImplementationService.class);

        new DeleteOrgDataTasklet(implementationService, "true", "true").execute(null, null);

        verify(implementationService).deleteImplementationData(true, true);
    }

    @Test
    public void aFailedJobReportsTheReasonInsteadOfAStackTrace() {
        JobExecution jobExecution = new JobExecution(1L, new JobParameters());
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.setExitStatus(ExitStatus.FAILED.addExitDescription(new ValidationException("Production or UAT organisation's data cannot be deleted")));
        jobExecution.addFailureException(new ValidationException("Production or UAT organisation's data cannot be deleted"));

        new DeleteOrgDataJobListener(null).afterJob(jobExecution);

        assertEquals("FAILED", jobExecution.getExitStatus().getExitCode());
        assertEquals("Production or UAT organisation's data cannot be deleted", jobExecution.getExitStatus().getExitDescription());
    }

    @Test
    public void aCompletedJobIsLeftAlone() {
        JobExecution jobExecution = new JobExecution(1L, new JobParameters());
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        new DeleteOrgDataJobListener(null).afterJob(jobExecution);

        assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());
    }
}
