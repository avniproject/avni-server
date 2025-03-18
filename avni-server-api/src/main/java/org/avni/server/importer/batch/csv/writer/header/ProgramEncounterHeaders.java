package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.domain.EncounterType;

public class ProgramEncounterHeaders extends CommonEncounterHeaders implements Headers {
    public final static  String enrolmentId = "Enrolment Id";

    public ProgramEncounterHeaders(EncounterType encounterType) {
        super(encounterType);
    }

    @Override
    public String[] getAllHeaders() {
        return new String[]{id, encounterTypeHeaderName, enrolmentId, visitDate, earliestVisitDate, maxVisitDate, encounterLocation, cancelLocation};
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping) {
        return getAllHeaders();
    }

    @Override
    public String[] getAllDescriptions(FormMapping formMapping) {
        return new String[0];
    }
}
