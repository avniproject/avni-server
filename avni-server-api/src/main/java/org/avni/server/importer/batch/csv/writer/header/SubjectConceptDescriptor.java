package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;

// decision concept cannot be of Subject type yet
public class SubjectConceptDescriptor extends FieldDescriptor {
    @Override
    public String getAllowedValues(FormElement fe) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("Enter UUID or id of %s from previous system. ", fe.getConcept().getName()));
        stringBuilder.append("UUID can be identified from address bar or longitudinal export. ");
        if (fe.isSingleSelect()) {
            stringBuilder.append("Only single value allowed.");
        } else {
            stringBuilder.append("Separate multiple values by comma.");
        }
        return stringBuilder.toString();
    }
}
