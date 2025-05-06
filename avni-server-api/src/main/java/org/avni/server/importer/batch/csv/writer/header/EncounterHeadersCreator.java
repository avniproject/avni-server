package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EncounterHeadersCreator extends AbstractHeaders implements HeaderCreator {

    public static final String ID = "Id from previous system";
    public static final String ENCOUNTER_TYPE = "Encounter Type";
    public static final String PROGRAM_ENROLMENT_ID = "Program Enrolment Id";
    public static final String SUBJECT_ID = "Subject Id";
    public static final String VISIT_DATE = "Visit Date";
    public static final String EARLIEST_VISIT_DATE = "Earliest Visit Date";
    public static final String MAX_VISIT_DATE = "Max Visit Date";
    public static final String ENCOUNTER_LOCATION = "Encounter Location";
    public static final String CANCEL_LOCATION = "Cancel Location";

    private final EncounterHeaderStrategyFactory strategyFactory;

    @Autowired
    public EncounterHeadersCreator(EncounterHeaderStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    @Override
    @Deprecated
    public String[] getAllHeaders() {
        throw new UnsupportedOperationException("Use getAllHeaders(FormMapping, Mode) instead");
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping, Object mode) {
        return buildFields(formMapping, mode).stream()
                .map(HeaderField::getHeader)
                .toArray(String[]::new);
    }

    @Override
    public String[] getAllDescriptions(FormMapping formMapping, Object mode) {
        return buildFields(formMapping, mode).stream()
                .map(HeaderField::getDescription)
                .toArray(String[]::new);
    }

    @Override
    protected List<HeaderField> buildFields(FormMapping formMapping, Object mode) {
        EncounterUploadMode encounterMode = (EncounterUploadMode) mode;
        EncounterHeaderStrategy strategy = strategyFactory.getStrategy(encounterMode);
        return strategy.generateHeaders(formMapping);
    }
}
