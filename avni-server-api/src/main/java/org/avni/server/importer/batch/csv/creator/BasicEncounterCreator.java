package org.avni.server.importer.batch.csv.creator;

import org.avni.server.domain.AbstractEncounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.importer.batch.csv.writer.header.CommonEncounterHeaders;
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

    public AbstractEncounter updateEncounter(Row row, AbstractEncounter basicEncounter, List<String> allErrorMsgs) throws Exception {
        DateCreator dateCreator = new DateCreator();
        LocalDate earliestVisitDate = dateCreator.getDate(
                row,
                CommonEncounterHeaders.earliestVisitDate,
                allErrorMsgs, null
        );
        if (earliestVisitDate != null)
            basicEncounter.setEarliestVisitDateTime(earliestVisitDate.toDateTimeAtStartOfDay());

        LocalDate maxVisitDate = dateCreator.getDate(
                row,
                CommonEncounterHeaders.maxVisitDate,
                allErrorMsgs, null
        );
        if (maxVisitDate != null) basicEncounter.setMaxVisitDateTime(maxVisitDate.toDateTimeAtStartOfDay());

        LocalDate visitDate = dateCreator.getDate(
                row,
                CommonEncounterHeaders.visitDate,
                allErrorMsgs, String.format("%s is mandatory", CommonEncounterHeaders.visitDate
                ));
        if (visitDate != null) basicEncounter.setEncounterDateTime(visitDate.toDateTimeAtStartOfDay(), userService.getCurrentUser());

        basicEncounter.setEncounterLocation(locationCreator.getLocation(row, CommonEncounterHeaders.encounterLocation, allErrorMsgs));
        basicEncounter.setCancelLocation(locationCreator.getLocation(row, CommonEncounterHeaders.cancelLocation, allErrorMsgs));
        EncounterType encounterType = encounterTypeCreator.getEncounterType(row.get(CommonEncounterHeaders.encounterTypeHeaderName), CommonEncounterHeaders.encounterTypeHeaderName);
        basicEncounter.setEncounterType(encounterType);
        return basicEncounter;
    }
}
