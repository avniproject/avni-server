package org.avni.server.importer.batch.csv.writer;

import org.avni.server.domain.ValidationException;
import org.avni.server.importer.batch.model.Row;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class LocationWriter implements ItemWriter<Row> {
    private final BulkLocationCreator bulkLocationCreator;
    private final BulkLocationEditor bulkLocationEditor;
    @Value("#{jobParameters['locationUploadMode']}")
    private String locationUploadMode;
    @Value("#{jobParameters['locationHierarchy']}")
    private String locationHierarchy;

    @Autowired
    public LocationWriter(BulkLocationCreator bulkLocationCreator, BulkLocationEditor bulkLocationEditor) {
        this.bulkLocationCreator = bulkLocationCreator;
        this.bulkLocationEditor = bulkLocationEditor;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws ValidationException {
        if (LocationUploadMode.isCreateMode(locationUploadMode)) {
            this.bulkLocationCreator.write(chunk.getItems(), locationHierarchy);
        } else {
            this.bulkLocationEditor.write(chunk.getItems());
        }
    }

    public enum LocationUploadMode {
        CREATE, EDIT;

        public static boolean isCreateMode(String mode) {
            return mode == null || LocationUploadMode.valueOf(mode).equals(CREATE);
        }

        public static boolean isCreateMode(LocationUploadMode mode) {
            return mode.equals(CREATE);
        }
    }
}
