package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.domain.Concept;

public class TextFieldDescriptor extends FieldDescriptor {
    @Override
    public String getFormat(Concept concept) {
        return "Any Text";
    }
}
