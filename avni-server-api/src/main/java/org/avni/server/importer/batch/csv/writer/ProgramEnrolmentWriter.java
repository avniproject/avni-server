package org.avni.server.importer.batch.csv.writer;

import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.csv.creator.ProgramEnrolmentRowCreator;
import org.avni.server.importer.batch.csv.writer.header.ProgramEnrolmentUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.OrganisationConfigService;
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
    private final String programEnrolmentUploadMode;

    @Autowired
    public ProgramEnrolmentWriter(ProgramEnrolmentRowCreator programEnrolmentRowCreator,
                                  OrganisationConfigService organisationConfigService,
                                  @Value("#{jobParameters['programEnrolmentUploadMode']}") String programEnrolmentUploadMode) {
        super(organisationConfigService);
        this.programEnrolmentRowCreator = programEnrolmentRowCreator;
        this.programEnrolmentUploadMode = programEnrolmentUploadMode;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws ValidationException, InvalidConfigurationException {
        ProgramEnrolmentUploadMode mode = resolveMode();
        for (Row row : chunk.getItems()) programEnrolmentRowCreator.create(row, mode);
    }

    private ProgramEnrolmentUploadMode resolveMode() {
        ProgramEnrolmentUploadMode mode = ProgramEnrolmentUploadMode.fromString(programEnrolmentUploadMode);
        return mode == null ? ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT : mode;
    }
}
