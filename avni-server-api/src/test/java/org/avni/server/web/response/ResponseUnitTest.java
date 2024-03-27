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
}
