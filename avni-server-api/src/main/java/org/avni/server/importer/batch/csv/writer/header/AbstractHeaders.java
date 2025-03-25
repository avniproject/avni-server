package org.avni.server.importer.batch.csv.writer.header;

import jakarta.annotation.PostConstruct;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.ImportHelperService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public abstract class AbstractHeaders implements Headers {
    protected ImportHelperService importHelperService;
    private final Map<String, FieldDescriptorStrategy> strategies = new HashMap<>();
    private final List<FieldDescriptorStrategy> strategyList;

    public AbstractHeaders(ImportHelperService importHelperService,
                           List<FieldDescriptorStrategy> strategyList) {
        this.importHelperService = importHelperService;
        this.strategyList = strategyList;
    }

    @PostConstruct
    public void initStrategies() {
        for (FieldDescriptorStrategy strategy : strategyList) {
            if (strategy instanceof CodedFieldDescriptor) {
                strategies.put(ConceptDataType.Coded.name(), strategy);
            } else if (strategy instanceof DateFieldDescriptor) {
                strategies.put(ConceptDataType.Date.name(), strategy);
            } else if (strategy instanceof TextFieldDescriptor) {
                strategies.put(ConceptDataType.Text.name(), strategy);
                strategies.put(ConceptDataType.Notes.name(), strategy);
            } else if (strategy instanceof NumericFieldDescriptor) {
                strategies.put(ConceptDataType.Numeric.name(), strategy);
            } else {
                strategies.put("default", strategy);
            }
        }
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
        FieldDescriptorStrategy strategy = strategies.getOrDefault(
                concept.getDataType(),
                strategies.get("default")
        );

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