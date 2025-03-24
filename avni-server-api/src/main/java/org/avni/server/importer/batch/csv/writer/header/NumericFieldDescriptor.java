package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;
import org.springframework.stereotype.Component;

@Component
public class NumericFieldDescriptor implements FieldDescriptorStrategy {
    @Override
    public String getAllowedValues(FormElement fe) {
        Concept concept = fe.getConcept();
        String allowedValues = "Allowed values: Any number";
        if (concept.getHighAbsolute() != null) {
            allowedValues = "Max value allowed: " + concept.getHighAbsolute();
        }
        if (concept.getLowAbsolute() != null) {
            allowedValues = "Min value allowed: " + concept.getLowAbsolute();
        }
        return allowedValues;
    }

    @Override
    public String getFormat(FormElement fe) {
        return null;
    }
}