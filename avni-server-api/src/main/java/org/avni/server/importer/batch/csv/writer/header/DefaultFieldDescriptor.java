package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.springframework.stereotype.Component;

@Component
public class DefaultFieldDescriptor implements FieldDescriptorStrategy {

    @Override
    public String getAllowedValues(FormElement fe) {
        return null;
    }

    @Override
    public String getFormat(FormElement fe) {
        return null;
    }
}