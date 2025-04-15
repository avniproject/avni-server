package org.avni.server.importer.batch.csv.writer.header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EncounterHeaderStrategyFactory {

    private final UploadVisitDetailsStrategy uploadVisitDetailsStrategy;
    private final ScheduleVisitStrategy scheduleVisitStrategy;

    @Autowired
    public EncounterHeaderStrategyFactory(UploadVisitDetailsStrategy uploadVisitDetailsStrategy,
                                          ScheduleVisitStrategy scheduleVisitStrategy) {
        this.uploadVisitDetailsStrategy = uploadVisitDetailsStrategy;
        this.scheduleVisitStrategy = scheduleVisitStrategy;
    }

    public EncounterHeaderStrategy getStrategy(EncounterUploadMode mode) {
        switch (mode) {
            case UPLOAD_VISIT_DETAILS:
                return uploadVisitDetailsStrategy;
            case SCHEDULE_VISIT:
                return scheduleVisitStrategy;
            default:
                throw new IllegalArgumentException("Unsupported EncounterUploadMode: " + mode);
        }
    }
}