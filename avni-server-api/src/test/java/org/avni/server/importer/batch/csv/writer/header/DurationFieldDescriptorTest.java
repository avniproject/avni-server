package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.application.TestFormElementBuilder;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

// avniproject/avni-server#959: the sample file says how to fill a duration column.
public class DurationFieldDescriptorTest {
    private final DurationFieldDescriptor descriptor = new DurationFieldDescriptor();
    private final Concept concept = new ConceptBuilder().withName("Session duration").withUuid("duration-uuid").withDataType(ConceptDataType.Duration).build();

    @Test
    public void shouldOfferAPlainNumberWhenTheQuestionHasOneUnit() {
        FormElement formElement = new TestFormElementBuilder().withConcept(concept).withDurationOptions("minutes").build();

        assertEquals("Format: a whole number of minutes, or a duration such as PT30M (30 minutes)", descriptor.getFormat(formElement));
    }

    @Test
    public void shouldAskForTheFullFormWhenTheQuestionHasSeveralUnits() {
        FormElement formElement = new TestFormElementBuilder().withConcept(concept).withDurationOptions("years", "months").build();

        assertEquals("Format: a duration such as PT30M (30 minutes) or P1Y6M (1 year 6 months)", descriptor.getFormat(formElement));
    }

    @Test
    public void shouldAskForTheFullFormWhenThereIsNoFormQuestion() {
        assertEquals("Format: a duration such as PT30M (30 minutes) or P1Y6M (1 year 6 months)", descriptor.getFormat(concept));
    }
}
