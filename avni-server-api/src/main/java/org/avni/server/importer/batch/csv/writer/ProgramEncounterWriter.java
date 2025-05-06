package org.avni.server.importer.batch.csv.writer;

import org.avni.server.importer.batch.csv.creator.ProgramEncounterCreator;
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
public class ProgramEncounterWriter extends EntityWriter implements ItemWriter<Row>, Serializable {
    private final ProgramEncounterCreator programEncounterCreator;
    private final String encounterUploadMode;

    @Autowired
    public ProgramEncounterWriter(ProgramEncounterCreator programEncounterCreator,
                                  OrganisationConfigService organisationConfigService,
                                  @Value("#{jobParameters['encounterUploadMode']}") String encounterUploadMode) {
        super(organisationConfigService);
        this.programEncounterCreator = programEncounterCreator;
        this.encounterUploadMode = encounterUploadMode;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws Exception {
        for (Row row : chunk.getItems()) write(row);
    }

    private void write(Row row) throws Exception {
        programEncounterCreator.create(row, encounterUploadMode);
    }
}
