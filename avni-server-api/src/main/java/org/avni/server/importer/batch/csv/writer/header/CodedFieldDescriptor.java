package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;

import java.util.stream.Collectors;

public class CodedFieldDescriptor extends FieldDescriptor {
    @Override
    public String getAllowedValues(FormElement fe) {
        Concept concept = fe.getConcept();
        String values = "Allowed values: {" + nonVoidedAnswerNames(concept) + "}";
        return fe.isSingleSelect() ? values + " Only single value allowed"
                : values + " Format: Separate multiple values by a comma";
    }

    @Override
    public String getAllowedValues(Concept concept) {
        String values = "Allowed values: {" + nonVoidedAnswerNames(concept) + "}";
        return values + " Format: May allow single value or multiple values separated a comma. Please check with developer.";
    }

    private static String nonVoidedAnswerNames(Concept concept) {
        return concept.getConceptAnswers().stream()
                .filter(ca -> !ca.isVoided() && !ca.getAnswerConcept().isVoided())
                .map(ca -> ca.getAnswerConcept().getName())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
