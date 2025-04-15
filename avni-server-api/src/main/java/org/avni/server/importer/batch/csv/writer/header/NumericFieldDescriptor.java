package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.domain.Concept;

public class NumericFieldDescriptor extends FieldDescriptor {
    @Override
    public String getAllowedValues(Concept concept) {
        Double low = concept.getLowAbsolute();
        Double high = concept.getHighAbsolute();

        if (low != null && high != null) {
            return "Min value allowed: " + low + " Max value allowed: " + high;
        } else if (low != null) {
            return "Min value allowed: " + low;
        } else if (high != null) {
            return "Max value allowed: " + high;
        } else {
            return "Allowed values: Any number";
        }
    }
}
