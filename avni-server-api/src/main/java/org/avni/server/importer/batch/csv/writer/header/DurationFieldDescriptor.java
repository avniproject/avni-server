package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;

import java.util.List;

public class DurationFieldDescriptor extends FieldDescriptor {
    private static final String FULL_FORM = "a duration such as PT30M (30 minutes)";

    @Override
    public String getFormat(FormElement fe) {
        List<String> units = fe.getDurationOptions();
        if (units.size() == 1) {
            return "Format: a whole number of " + units.get(0) + ", or " + FULL_FORM;
        }
        return this.getFormat(fe.getConcept());
    }

    @Override
    public String getFormat(Concept concept) {
        return "Format: " + FULL_FORM + " or P1Y6M (1 year 6 months)";
    }
}
