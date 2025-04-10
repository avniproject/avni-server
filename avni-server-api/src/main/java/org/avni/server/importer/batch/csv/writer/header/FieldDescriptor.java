package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;

public abstract class FieldDescriptor {
    public String getAllowedValues(FormElement fe) {
        return this.getAllowedValues(fe.getConcept());
    }

    public String getAllowedValues(Concept concept) {
        return null;
    }

    public String getFormat(FormElement fe) {
        return this.getFormat(fe.getConcept());
    }

    public String getFormat(Concept concept) {
        return null;
    }
}
