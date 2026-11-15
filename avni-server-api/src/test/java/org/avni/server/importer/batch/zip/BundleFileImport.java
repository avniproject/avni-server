package org.avni.server.importer.batch.zip;

import org.avni.server.domain.User;
import org.avni.server.importer.batch.model.BundleFile;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.test.JobScopeTestUtils;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.StepScopeTestUtils;
import org.springframework.context.ApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// One file of a bundle through the importer bean the upload job uses, inside the upload's job and step, with the job
// parameters an upload gives it. The step is returned for its counts; its job parameter "uuid" names the error file.
final class BundleFileImport {
    private BundleFileImport() {
    }

    static StepExecution importFile(ApplicationContext context, User user, String organisationUuid, String fileName, String content) throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("userId", user.getId())
                .addString("organisationUUID", organisationUuid)
                .addString("uuid", UUID.randomUUID().toString())
                .toJobParameters();
        JobExecution execution = MetaDataInstanceFactory.createJobExecution("importZipJob", 1L, 1L, parameters);
        StepExecution step = execution.createStepExecution("importZipStep");
        JobScopeTestUtils.doInJobScope(execution, () -> StepScopeTestUtils.doInStepScope(step, () -> {
            context.getBean(BundleZipFileImporter.class)
                    .write(new Chunk<>(new BundleFile(fileName, content.getBytes(StandardCharsets.UTF_8))));
            return null;
        }));
        return step;
    }
}
