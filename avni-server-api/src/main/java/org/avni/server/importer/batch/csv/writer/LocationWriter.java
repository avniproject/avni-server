package org.avni.server.importer.batch.csv.writer;

import org.avni.server.importer.batch.model.Row;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@StepScope
@Component
public class LocationWriter implements ItemWriter<Row> {
    private final BulkLocationCreator bulkLocationCreator;
    private final BulkLocationEditor bulkLocationEditor;
    @Value("#{jobParameters['locationUploadMode']}")
    private String locationUploadMode;
    @Value("#{jobParameters['locationHierarchy']}")
    private String locationHierarchy;
    private List<String> editLocationTypeNames;

    @Autowired
    public LocationWriter(BulkLocationCreator bulkLocationCreator, BulkLocationEditor bulkLocationEditor) {
        this.bulkLocationCreator = bulkLocationCreator;
        this.bulkLocationEditor = bulkLocationEditor;
    }

    @PostConstruct
    public void init() {
        this.editLocationTypeNames = this.bulkLocationCreator.getLocationTypeNames();
    }

    @Override
    public void write(List<? extends Row> rows) {
        List<String> allErrorMsgs = new ArrayList<>();
        if (LocationUploadMode.isCreateMode(locationUploadMode)) {
            this.bulkLocationCreator.write(rows, locationHierarchy);
        } else {
            this.bulkLocationEditor.write(rows, allErrorMsgs);
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
