package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;
import org.springframework.stereotype.Component;

@Component
public class NumericFieldDescriptor implements FieldDescriptorStrategy {
    @Override
    public String getAllowedValues(FormElement fe) {
        Concept concept = fe.getConcept();
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

    @Override
    public String getFormat(FormElement fe) {
        return null;
    }
}