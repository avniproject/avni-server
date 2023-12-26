package org.avni.server.web.response;

import org.avni.server.domain.ConceptDataType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.service.ConceptService;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ResponseUnitTest {
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptService conceptService;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test()
    public void shouldNotAlterExistingObsWhenPassedNullObservations() {

        LinkedHashMap<String, Object> parentMap = new LinkedHashMap<>();
        LinkedHashMap<String, Object> observationsResponse = new LinkedHashMap<>();
        observationsResponse.put("First Name", "Test");

        Response.putObservations(conceptRepository, conceptService, parentMap, observationsResponse, null);
        LinkedHashMap<String, Object> observations = (LinkedHashMap<String, Object>) parentMap.get("observations");
        assertThat(observations.size(), is(1));
        assertThat(observations.get("First Name"), is("Test"));
    }

    @Test()
    public void shouldNotAlterExistingObsWhenPassedEmptyObservations() throws Exception {

        LinkedHashMap<String, Object> parentMap = new LinkedHashMap<>();
        LinkedHashMap<String, Object> observationsResponse = new LinkedHashMap<>();
        observationsResponse.put("First Name", "Test");

        Response.putObservations(conceptRepository, conceptService, parentMap, observationsResponse, new ObservationCollection());
        LinkedHashMap<String, Object> observations = (LinkedHashMap<String, Object>) parentMap.get("observations");
        assertThat(observations.size(), is(1));
        assertThat(observations.get("First Name"), is("Test"));
    }

    @Test()
    public void shouldAddObservationsWhenPassedObsAreNotEmpty() {
        String questionConceptName = "ABC";
        String answerValue = "XYZ";
        LinkedHashMap<String, Object> parentMap = new LinkedHashMap<>();
        LinkedHashMap<String, Object> observationsResponse = new LinkedHashMap<>();
        observationsResponse.put("First Name", "Test");
        ObservationCollection observations = new ObservationCollection();
        String questionConceptUuid = "55f3e0cc-a9bc-45d6-a42c-a4fd3d90465f";
        String answerConceptUuid = "a33da2f8-7329-4e2a-8c27-046ee4082524";
        Concept questionConcept = new Concept();
        questionConcept.setName(questionConceptName);
        Concept answerConcept = new Concept();
        answerConcept.setUuid(answerConceptUuid);
        answerConcept.setName(answerValue);
        ConceptAnswer conceptAnswer = new ConceptAnswer();
        conceptAnswer.setConcept(answerConcept);
        Set<ConceptAnswer> answers = new HashSet<>();
        answers.add(conceptAnswer);
        questionConcept.setConceptAnswers(answers);
        observations.put(questionConceptUuid, answerConceptUuid);
        ConceptNameUuidAndDatatype conceptMap1 = new ConceptNameUuidAndDatatype(questionConceptUuid, questionConceptName, ConceptDataType.Coded);
        ConceptNameUuidAndDatatype conceptMap2 = new ConceptNameUuidAndDatatype(answerConceptUuid, answerValue, ConceptDataType.NA);
        List<ConceptNameUuidAndDatatype> conceptMapList = Arrays.asList(conceptMap1, conceptMap2);
        when(conceptRepository.findAllConceptsInObs(anyString())).thenReturn(conceptMapList);
        when(conceptService.getObservationValue(any(), anyMap(), anyString())).thenReturn(answerValue);
        Response.putObservations(conceptRepository, conceptService, parentMap, observationsResponse, observations);
        LinkedHashMap<String, Object> result = (LinkedHashMap<String, Object>) parentMap.get("observations");

        assertThat(result.get("First Name"), is("Test"));
        assertThat(result.get(questionConceptName), is(answerValue));
    }
}
