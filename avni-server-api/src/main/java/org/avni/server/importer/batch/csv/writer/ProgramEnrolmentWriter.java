package org.avni.server.importer.batch.csv.writer;

import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.creator.ProgramEnrolmentRowCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.ProgramImportService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@StepScope
@Component
public class ProgramEnrolmentWriter extends EntityWriter implements ItemWriter<Row>, Serializable {
    private final ProgramEnrolmentRowCreator programEnrolmentRowCreator;
    private final ProgramImportService programImportService;
    private final String programEnrolmentUploadMode;
    private final String type;

    @Autowired
    public ProgramEnrolmentWriter(ProgramEnrolmentRowCreator programEnrolmentRowCreator,
                                  OrganisationConfigService organisationConfigService,
                                  ProgramImportService programImportService,
                                  @Value("#{jobParameters['programEnrolmentUploadMode']}") String programEnrolmentUploadMode,
                                  @Value("#{jobParameters['type']}") String type) {
        super(organisationConfigService);
        this.programEnrolmentRowCreator = programEnrolmentRowCreator;
        this.programImportService = programImportService;
        this.programEnrolmentUploadMode = programEnrolmentUploadMode;
        this.type = type;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws ValidationException, InvalidConfigurationException {
        ProgramEnrolmentUploadMode mode = ProgramEnrolmentUploadMode.parseOrDefault(programEnrolmentUploadMode);
        for (Row row : chunk.getItems()) {
            if (mode == ProgramEnrolmentUploadMode.EXIT_ENROLMENT) {
                // Looked up per row, not cached: each row commits in its own transaction (chunk size 1).
                programEnrolmentRowCreator.exit(row, programImportService.getFormMapping(type.split("---")));
            } else {
                programEnrolmentRowCreator.create(row, mode);
            }
        }
    }
}
