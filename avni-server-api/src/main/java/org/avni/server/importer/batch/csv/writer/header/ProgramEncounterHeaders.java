package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.SubjectType;

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
    public String[] getAllHeaders(SubjectType subjectType, FormMapping formMapping) {
        return getAllHeaders();
    }
}
