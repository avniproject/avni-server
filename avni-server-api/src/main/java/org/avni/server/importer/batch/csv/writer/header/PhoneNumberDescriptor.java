package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;

public class PhoneNumberDescriptor implements FieldDescriptorStrategy{
    @Override
    public String getAllowedValues(FormElement fe) {
        return "Enter mobile number";
    }

    @Override
    public String getFormat(FormElement fe) {
        return null;
    }
}
