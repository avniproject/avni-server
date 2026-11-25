package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;
import org.avni.server.importer.batch.csv.DurationCellFormat;

import java.util.Collections;

public class DurationFieldDescriptor extends FieldDescriptor {
    @Override
    public String getFormat(FormElement fe) {
        return "Format: " + DurationCellFormat.whatToEnter(fe.getDurationOptions());
    }

    @Override
    public String getFormat(Concept concept) {
        return "Format: " + DurationCellFormat.whatToEnter(Collections.emptyList());
    }
}
