package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.ImportService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public abstract class AbstractHeaders implements HeaderCreator {
    private static final Map<String, Supplier<FieldDescriptor>> STRATEGY_MAP = new HashMap<>();

    static {
        STRATEGY_MAP.put(ConceptDataType.Coded.name(), CodedFieldDescriptor::new);
        STRATEGY_MAP.put(ConceptDataType.Date.name(), DateFieldDescriptor::new);
        STRATEGY_MAP.put(ConceptDataType.Text.name(), TextFieldDescriptor::new);
        STRATEGY_MAP.put(ConceptDataType.Numeric.name(), NumericFieldDescriptor::new);
        STRATEGY_MAP.put(ConceptDataType.PhoneNumber.name(), PhoneNumberDescriptor::new);
        STRATEGY_MAP.put(ConceptDataType.Subject.name(), SubjectConceptDescriptor::new);
    }

    private static FieldDescriptor getStrategy(String dataType) {
        return STRATEGY_MAP.getOrDefault(dataType, DefaultFieldDescriptor::new).get();
    }

    @Override
    public String[] getAllHeaders() {
        throw new UnsupportedOperationException("Use getAllHeaders(formMapping, Mode) with runtime data instead");
    }

    public String[] getAllMandatoryHeaders(FormMapping formMapping, Mode mode) throws InvalidConfigurationException {
        return buildFields(formMapping, mode).stream()
                .filter(HeaderField::isMandatory).map(HeaderField::getHeader)
                .toArray(String[]::new);
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping, Mode mode) throws InvalidConfigurationException {
        return buildFields(formMapping, mode).stream()
                .map(HeaderField::getHeader)
                .toArray(String[]::new);
    }

    @Override
    public String[] getConceptHeaders(FormMapping formMapping, String[] fileHeaders) {
        List<HeaderField> fields = new ArrayList<>();
        fields.addAll(generateConceptFields(formMapping));
        fields.addAll(generateDecisionConceptFields(formMapping.getForm()));
        return fields.stream()
                .map(HeaderField::getHeader)
                .toArray(String[]::new);
    }

    @Override
    public String[] getAllDescriptions(FormMapping formMapping, Mode mode) throws InvalidConfigurationException {
        return buildFields(formMapping, mode).stream()
                .map(HeaderField::getDescription)
                .toArray(String[]::new);
    }

    protected abstract List<HeaderField> buildFields(FormMapping formMapping, Mode mode) throws InvalidConfigurationException;

    protected static List<HeaderField> generateConceptFields(FormMapping formMapping) {
        return formMapping.getForm().getApplicableFormElements().stream()
                .filter(fe -> !ConceptDataType.isQuestionGroup(fe.getConcept().getDataType()))
                .map(AbstractHeaders::mapFormElementToField)
                .collect(Collectors.toList());
    }

    protected List<HeaderField> generateDecisionConceptFields(Form form) {
        return form.getDecisionConcepts().stream().map(this::mapDecisionConceptToField).toList();
    }

    private HeaderField mapDecisionConceptToField(Concept concept) {
        FieldDescriptor strategy = getStrategy(concept.getDataType());
        String format = strategy.getFormat(concept);
        return new HeaderField("\"" + concept.getName() + "\"", "", false, strategy.getAllowedValues(concept), format, null, false);
    }

    private static HeaderField mapFormElementToField(FormElement fe) {
        Concept concept = fe.getConcept();
        String header = ImportService.getHeaderName(fe);
        FieldDescriptor strategy = getStrategy(concept.getDataType());

        String allowedValues = strategy.getAllowedValues(fe);
        String format = strategy.getFormat(fe);

        return new HeaderField(header, "", fe.isMandatory(), allowedValues, format, null, true);
    }
}
