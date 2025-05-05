package org.avni.server.service;

import org.avni.server.application.*;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.web.external.request.export.ExportFilters;
import org.avni.server.web.request.ObservationRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ObservationServiceTest {

    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock
    private FormRepository formRepository;
    @Mock
    private EnhancedValidationService enhancedValidationService;

    private ObservationService observationService;

    @Before
    public void setup() {
        initMocks(this);
        observationService = new ObservationService(conceptRepository, individualRepository, locationRepository, namedParameterJdbcTemplate, Optional.of(enhancedValidationService));
    }

    @Test
    public void shouldIgnoreObservationsWithNullEquivalentAnswers() {
        Concept abc = new Concept();
        Concept efg = new Concept();
        ConceptAnswer abc_efg = new ConceptAnswer();
        efg.setName("EFG");
        efg.setUuid("EFG-EFG");

        abc_efg.setAnswerConcept(efg);
        abc_efg.setConcept(abc);

        abc.setName("ABC");
        abc.setUuid("ABC-ABC");
        abc.setDataType("Coded");
        abc.setConceptAnswers(new HashSet<>());
        abc.addAnswer(abc_efg);

        when(conceptRepository.findByName("ABC")).thenReturn(abc);
        when(conceptRepository.findByName("EFG")).thenReturn(efg);

        ObservationRequest req0 = new ObservationRequest();
        ObservationRequest req1 = new ObservationRequest();
        ObservationRequest req2 = new ObservationRequest();
        ObservationRequest req3 = new ObservationRequest();

        req0.setConceptName("ABC");
        req0.setValue(null);
        req1.setConceptName("ABC");
        req1.setValue("null");
        req2.setConceptName("ABC");
        req2.setValue("NULL");
        req3.setConceptName("ABC");
        req3.setValue("EFG-EFG");

        List<ObservationRequest> requests = Arrays.asList(req0, req1, req2, req3);

        ObservationCollection observationCollection = observationService.createObservations(requests);

        assertEquals(1, observationCollection.size());
    }

    @Test
    public void shouldReplaceConceptUUIDWithMappedConcept() {
        // Given
        String originalConceptUuid = "0e1dab85-dc65-419a-9278-2095e2849b63";
        String replacementConceptUuid = "b103929e-2832-4ad5-9b17-db8536925ec3";

        Concept replacementConcept = new Concept();
        replacementConcept.setUuid(replacementConceptUuid);
        replacementConcept.setName("Replacement Concept");

        when(conceptRepository.findByUuid(replacementConceptUuid)).thenReturn(replacementConcept);

        ObservationRequest observationRequest = new ObservationRequest();
        observationRequest.setConceptUUID(originalConceptUuid);
        observationRequest.setValue("some value");

        // When
        ObservationCollection observationCollection = observationService.createObservations(Collections.singletonList(observationRequest));

        // Then
        assertEquals(1, observationCollection.size());
        assertEquals("some value", observationCollection.get(replacementConceptUuid));
    }

    @Test
    public void testGetAsSingleStringValue() {
        ObservationCollection observationCollection = new ObservationCollection();
        assertEquals("8ebbf088-f292-483e-9084-7de919ce67b7",
                observationCollection.getAsSingleStringValue("[8ebbf088-f292-483e-9084-7de919ce67b7]"));
        assertEquals("[8ebbf088-f292-483e-9084-7de919ce67b7,a77bd700-1409-4d52-93bc-9fe32c0e169b]",
                observationCollection.getAsSingleStringValue("[8ebbf088-f292-483e-9084-7de919ce67b7,a77bd700-1409-4d52-93bc-9fe32c0e169b]"));
        assertEquals("yes",
                observationCollection.getAsSingleStringValue("yes"));
        assertEquals("yes,no",
                observationCollection.getAsSingleStringValue("yes,no"));
        assertEquals("[yes,no]",
                observationCollection.getAsSingleStringValue("[yes,no]"));
    }

    @Test
    public void getMaxNumberOfObservationSets() {
        Concept groupConcept1 = new ConceptBuilder().withName("GC1").withUuid("gc1").withId(1).withDataType(ConceptDataType.QuestionGroup).build();
        Concept groupConcept1Concept1 = new ConceptBuilder().withName("GC1-C1").withId(2).withUuid("gc1-c1").withDataType(ConceptDataType.Text).build();
        Concept groupConcept2 = new ConceptBuilder().withName("GC2").withId(3).withDataType(ConceptDataType.QuestionGroup).withUuid("gc2").build();
        Concept groupConcept2Concept1 = new ConceptBuilder().withId(4).withName("GC2-C1").withUuid("gc2-c1").withDataType(ConceptDataType.Text).build();

        FormElement groupFormElement1 = new TestFormElementBuilder().withConcept(groupConcept1).withId(1).withRepeatable(true).build();
        FormElement formElement1_1 = new TestFormElementBuilder().withQuestionGroupElement(groupFormElement1).withId(2).withConcept(groupConcept1Concept1).build();

        FormElement groupFormElement2 = new TestFormElementBuilder().withId(3).withConcept(groupConcept2).build();
        FormElement formElement2_1 = new TestFormElementBuilder().withId(4).withQuestionGroupElement(groupFormElement2).withConcept(groupConcept2Concept1).build();

        FormElementGroup formElementGroup = new TestFormElementGroupBuilder().addFormElement(groupFormElement1, formElement1_1, groupFormElement2, formElement2_1).build();

        Form form = new TestFormBuilder().addFormElementGroup(formElementGroup).build();

        when(namedParameterJdbcTemplate.query(any(String.class), any(HashMap.class), any(ObservationService.CountMapper.class))).thenReturn(Collections.singletonList(2));

        HashMap<Form, ExportFilters> map = new HashMap<>();
        map.put(form, new ExportFilters());
        Map<FormElement, Integer> maxNumberOfObservationSets = observationService.getMaxNumberOfQuestionGroupObservations(map, TimeZone.getDefault().getDisplayName());
        assertEquals(2, maxNumberOfObservationSets.get(groupFormElement1).intValue());
        assertEquals(null, maxNumberOfObservationSets.get(groupFormElement2));
    }
}
