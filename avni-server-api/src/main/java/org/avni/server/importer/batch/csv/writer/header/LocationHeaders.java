package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.domain.SubjectType;

public class LocationHeaders implements Headers {
    public final static String gpsCoordinates = "GPS coordinates";

    @Override
    public String[] getAllHeaders() {
        return new String[]{gpsCoordinates};
    }

    @Override
    public String[] getAllHeaders(SubjectType subjectType, FormMapping formMapping) {
        return getAllHeaders();
    }
}
