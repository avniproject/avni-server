package org.avni.server.importer.batch.zip;

import org.avni.server.domain.User;
import org.avni.server.importer.batch.model.BundleFile;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.test.JobScopeTestUtils;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.context.ApplicationContext;

import java.nio.charset.StandardCharsets;

// One file of a bundle through the importer bean the upload job uses, with the job parameters an upload gives it.
final class BundleFileImport {
    private BundleFileImport() {
    }

    static void importFile(ApplicationContext context, User user, String organisationUuid, String fileName, String content) throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("userId", user.getId())
                .addString("organisationUUID", organisationUuid)
                .toJobParameters();
        JobExecution execution = MetaDataInstanceFactory.createJobExecution("importZipJob", 1L, 1L, parameters);
        JobScopeTestUtils.doInJobScope(execution, () -> {
            context.getBean(BundleZipFileImporter.class)
                    .write(new Chunk<>(new BundleFile(fileName, content.getBytes(StandardCharsets.UTF_8))));
            return null;
        });
    }
}
