package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

public class CodedFieldDescriptor implements FieldDescriptorStrategy {
    @Override
    public String getAllowedValues(FormElement fe) {
        Concept concept = fe.getConcept();
        String values = "Allowed values: {" + concept.getConceptAnswers().stream()
                .map(ca -> ca.getAnswerConcept().getName())
                .collect(Collectors.joining(", ")) + "}";
        return fe.isSingleSelect() ? values + " Only single value allowed."
                : values + " Format: Separate multiple values by a comma.";
    }

    @Override
    public String getFormat(FormElement fe) {
        return null;
    }
}
