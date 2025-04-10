package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.domain.Concept;
import org.springframework.stereotype.Component;

public class DateFieldDescriptor extends FieldDescriptor {
    @Override
    public String getFormat(Concept concept) {
        return "Format: DD-MM-YYYY";
    }
}
