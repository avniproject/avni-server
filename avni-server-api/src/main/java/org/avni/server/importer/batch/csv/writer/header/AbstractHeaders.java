package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.ImportHelperService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public abstract class AbstractHeaders implements Headers {
    protected ImportHelperService importHelperService;

    AbstractHeaders(ImportHelperService importHelperService){
        this.importHelperService = importHelperService;

    }

    @Override
    public String[] getAllHeaders() {
        throw new UnsupportedOperationException("Use getAllHeaders(SubjectType, FormMapping) with runtime data instead");
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping) {
        return buildFields(formMapping).stream()
                .map(HeaderField::getHeader)
                .toArray(String[]::new);
    }

    public String[] getAllDescriptions(FormMapping formMapping) {
        return buildFields(formMapping).stream()
                .map(HeaderField::getDescription)
                .toArray(String[]::new);
    }

    protected abstract List<HeaderField> buildFields(FormMapping formMapping);

    protected List<HeaderField> generateConceptFields(FormMapping formMapping) {
        return formMapping.getForm().getApplicableFormElements().stream()
                .filter(fe -> !ConceptDataType.isQuestionGroup(fe.getConcept().getDataType()))
                .map(this::mapFormElementToField)
                .collect(Collectors.toList());
    }

    protected HeaderField mapFormElementToField(FormElement fe) {
        Concept concept = fe.getConcept();
        String header = importHelperService.getHeaderName(fe);
        String allowedValues = null;
        String format = null;
        String editable = null;

        if (ConceptDataType.matches(ConceptDataType.Coded, concept.getDataType())) {
            allowedValues = "Allowed values: {" + concept.getConceptAnswers().stream()
                    .map(ca -> ca.getAnswerConcept().getName())
                    .collect(Collectors.joining(", ")) + "}";
        } else if (ConceptDataType.matches(ConceptDataType.Date, concept.getDataType())) {
            format = "Format: DD-MM-YYYY";
        } else if (ConceptDataType.matches(ConceptDataType.Text, concept.getDataType()) || ConceptDataType.matches(ConceptDataType.Notes, concept.getDataType())) {
            format = "Any Text";
        } else if (ConceptDataType.matches(ConceptDataType.Numeric, concept.getDataType())) {
            allowedValues = "Allowed values: Any number";
            if (concept.getHighAbsolute() != null) allowedValues = "Max value allowed: " + concept.getHighAbsolute();
            if (concept.getLowAbsolute() != null) allowedValues = "Min value allowed: " + concept.getLowAbsolute();
        }

        if (fe.getKeyValues() !=null && fe.getKeyValues().getKeyValue(KeyType.editable) !=null && fe.getKeyValues().getKeyValue(KeyType.editable).getValue().equals(false)) {
            editable = " | The value can be auto-calculated if not entered";
        }

        return new HeaderField(header, "", fe.isMandatory(), allowedValues, format, editable);
    }
}