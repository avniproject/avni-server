package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;

public interface FieldDescriptorStrategy {
    String getAllowedValues(FormElement fe);
    String getFormat(FormElement fe);
}