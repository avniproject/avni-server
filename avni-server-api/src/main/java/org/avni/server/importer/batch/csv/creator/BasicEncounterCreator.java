package org.avni.server.importer.batch.csv.creator;

import org.avni.server.domain.AbstractEncounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.importer.batch.csv.writer.header.EncounterHeadersCreator;
import org.avni.server.importer.batch.csv.writer.header.EncounterUploadMode;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.UserService;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class BasicEncounterCreator {
    private final LocationCreator locationCreator;
    private final EncounterTypeCreator encounterTypeCreator;
    private final UserService userService;

    @Autowired
    public BasicEncounterCreator(EncounterTypeCreator encounterTypeCreator, UserService userService) {
        this.encounterTypeCreator = encounterTypeCreator;
        this.userService = userService;
        this.locationCreator = new LocationCreator();
    }

    public void updateEncounterFields(Row row, AbstractEncounter basicEncounter, List<String> allErrorMsgs, EncounterUploadMode mode) {
        DateCreator dateCreator = new DateCreator();
        if (EncounterUploadMode.SCHEDULE_VISIT == mode) {
            LocalDate earliestVisitDate = dateCreator.getDate(
                    row,
                    EncounterHeadersCreator.EARLIEST_VISIT_DATE,
                    allErrorMsgs, null
            );
            if (earliestVisitDate != null)
                basicEncounter.setEarliestVisitDateTime(earliestVisitDate.toDateTimeAtStartOfDay());
            LocalDate maxVisitDate = dateCreator.getDate(
                    row,
                    EncounterHeadersCreator.MAX_VISIT_DATE,
                    allErrorMsgs, null
            );
            if (maxVisitDate != null) basicEncounter.setMaxVisitDateTime(maxVisitDate.toDateTimeAtStartOfDay());
        } else {
            LocalDate visitDate = row.ensureDateIsPresentAndNotInFuture(EncounterHeadersCreator.VISIT_DATE, allErrorMsgs);
            if (visitDate != null)
                basicEncounter.setEncounterDateTime(visitDate.toDateTimeAtStartOfDay(), userService.getCurrentUser());
            basicEncounter.setEncounterLocation(locationCreator.getGeoLocation(row, EncounterHeadersCreator.ENCOUNTER_COORDINATES, allErrorMsgs));
        }

        EncounterType encounterType = encounterTypeCreator.getEncounterType(row.get(EncounterHeadersCreator.ENCOUNTER_TYPE), EncounterHeadersCreator.ENCOUNTER_TYPE);
        basicEncounter.setEncounterType(encounterType);
    }
}
