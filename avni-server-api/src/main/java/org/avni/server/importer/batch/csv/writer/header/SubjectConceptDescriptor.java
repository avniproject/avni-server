package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;

public class SubjectConceptDescriptor implements FieldDescriptorStrategy{
    private String conceptName;
    SubjectConceptDescriptor(String conceptName){
        this.conceptName = conceptName;
    }
    @Override
    public String getAllowedValues(FormElement fe) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("Enter UUID or id of %s from previous system. ",conceptName));
        stringBuilder.append("UUID can be identified from address bar or longitudinal export. ");
        if(fe.isSingleSelect()){
            stringBuilder.append("Only single value allowed.");
        }
        else{
            stringBuilder.append("Separate multiple values by comma.");
        }
        return stringBuilder.toString();
    }

    @Override
    public String getFormat(FormElement fe) {
        return null;
    }
}
