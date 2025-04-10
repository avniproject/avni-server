package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.domain.Concept;

public class PhoneNumberDescriptor extends FieldDescriptor {
    @Override
    public String getAllowedValues(Concept concept) {
        return "Enter mobile number";
    }
}
