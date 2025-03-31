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

    public AbstractHeaders(ImportHelperService importHelperService) {
        this.importHelperService = importHelperService;
    }

    private FieldDescriptorStrategy getStrategy(String dataType) {
        if (dataType.equals(ConceptDataType.Coded.name())) {
            return new CodedFieldDescriptor();
        } else if (dataType.equals(ConceptDataType.Date.name())) {
            return new DateFieldDescriptor();
        } else if (dataType.equals(ConceptDataType.Text.name())) {
            return new TextFieldDescriptor();
        } else if (dataType.equals(ConceptDataType.Numeric.name())) {
            return new NumericFieldDescriptor();
        } else {
            return new DefaultFieldDescriptor();
        }
    }

    @Override
    public String[] getAllHeaders() {
        throw new UnsupportedOperationException("Use getAllHeaders(FormMapping) with runtime data instead");
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
        FieldDescriptorStrategy strategy = getStrategy(concept.getDataType());

        String allowedValues = strategy.getAllowedValues(fe);
        String format = strategy.getFormat(fe);
        String editable = null;

        if (fe.getKeyValues() != null &&
                fe.getKeyValues().getKeyValue(KeyType.editable) != null &&
                fe.getKeyValues().getKeyValue(KeyType.editable).getValue().equals(false)) {
            editable = "The value can be auto-calculated if not entered";
        }

        return new HeaderField(header, "", fe.isMandatory(), allowedValues, format, editable, false);
    }
}
