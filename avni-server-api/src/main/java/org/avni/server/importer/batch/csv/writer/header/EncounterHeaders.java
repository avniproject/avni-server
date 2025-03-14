package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.SubjectType;

public class EncounterHeaders extends CommonEncounterHeaders implements Headers {
    public final static String subjectId = "Subject Id";

    public EncounterHeaders(EncounterType encounterType) {
        super(encounterType);
    }

    @Override
    public String[] getAllHeaders() {
        return new String[]{id, encounterTypeHeaderName, subjectId, visitDate, earliestVisitDate, maxVisitDate, encounterLocation, cancelLocation};
    }

    @Override
    public String[] getAllHeaders(SubjectType subjectType, FormMapping formMapping) {
        return getAllHeaders();
    }
}
