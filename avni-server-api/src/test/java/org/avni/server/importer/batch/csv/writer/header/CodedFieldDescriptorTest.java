package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodedFieldDescriptorTest {

    private static Concept namedConcept(String name) {
        Concept concept = new Concept();
        concept.setName(name);
        return concept;
    }

    private static ConceptAnswer answer(Concept answerConcept, boolean answerVoided, boolean answerConceptVoided) {
        ConceptAnswer ca = new ConceptAnswer();
        ca.setAnswerConcept(answerConcept);
        ca.setVoided(answerVoided);
        answerConcept.setVoided(answerConceptVoided);
        return ca;
    }

    private static Concept codedConceptWith(ConceptAnswer... answers) {
        Concept concept = new Concept();
        concept.setName("Reason");
        Set<ConceptAnswer> set = new LinkedHashSet<>();
        for (ConceptAnswer ca : answers) set.add(ca);
        concept.setConceptAnswers(set);
        return concept;
    }

    @Test
    public void getAllowedValues_FormElement_excludesVoidedAnswerRowsAndVoidedAnswerConcepts() {
        Concept active1 = namedConcept("Active Reason A");
        Concept active2 = namedConcept("Active Reason B");
        Concept voidedAnswerRow = namedConcept("Stale Reason");
        Concept voidedAnswerConcept = namedConcept("Deleted Reason");

        Concept reason = codedConceptWith(
                answer(active1, false, false),
                answer(active2, false, false),
                answer(voidedAnswerRow, true, false),
                answer(voidedAnswerConcept, false, true)
        );

        FormElement fe = new FormElement();
        fe.setConcept(reason);
        fe.setType("SingleSelect");

        String allowed = new CodedFieldDescriptor().getAllowedValues(fe);

        assertTrue(allowed, allowed.contains("Active Reason A"));
        assertTrue(allowed, allowed.contains("Active Reason B"));
        assertFalse(allowed, allowed.contains("Stale Reason"));
        assertFalse(allowed, allowed.contains("Deleted Reason"));
        assertEquals("Allowed values: {Active Reason A, Active Reason B} Only single value allowed", allowed);
    }

    @Test
    public void getAllowedValues_Concept_excludesVoidedAnswerRowsAndVoidedAnswerConcepts() {
        Concept active = namedConcept("Active");
        Concept voidedRow = namedConcept("Old Row");
        Concept voidedConcept = namedConcept("Deleted");

        Concept reason = codedConceptWith(
                answer(active, false, false),
                answer(voidedRow, true, false),
                answer(voidedConcept, false, true)
        );

        String allowed = new CodedFieldDescriptor().getAllowedValues(reason);

        assertTrue(allowed, allowed.contains("Active"));
        assertFalse(allowed, allowed.contains("Old Row"));
        assertFalse(allowed, allowed.contains("Deleted"));
        assertTrue(allowed, allowed.startsWith("Allowed values: {Active}"));
    }

    @Test
    public void getAllowedValues_FormElement_multiSelectFormatHint_isPreserved() {
        Concept a = namedConcept("Alpha");
        Concept b = namedConcept("Bravo");
        Concept reason = codedConceptWith(answer(a, false, false), answer(b, false, false));
        FormElement fe = new FormElement();
        fe.setConcept(reason);
        fe.setType("MultiSelect");

        String allowed = new CodedFieldDescriptor().getAllowedValues(fe);

        assertEquals("Allowed values: {Alpha, Bravo} Format: Separate multiple values by a comma", allowed);
    }

    @Test
    public void getAllowedValues_FormElement_allAnswersVoided_emitsEmptyBraces() {
        Concept a = namedConcept("A");
        Concept b = namedConcept("B");
        Concept reason = codedConceptWith(answer(a, true, false), answer(b, false, true));
        FormElement fe = new FormElement();
        fe.setConcept(reason);
        fe.setType("SingleSelect");

        String allowed = new CodedFieldDescriptor().getAllowedValues(fe);

        assertEquals("Allowed values: {} Only single value allowed", allowed);
    }
}
