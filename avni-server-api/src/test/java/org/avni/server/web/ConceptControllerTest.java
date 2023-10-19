package org.avni.server.web;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.projection.ConceptProjection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.util.MockUtil.resetMock;

public class ConceptControllerTest {
    ConceptController conceptController;

    @Mock
    ConceptRepository conceptRepository;

    ProjectionFactory projectionFactory;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        projectionFactory = new SpelAwareProxyProjectionFactory();
        conceptController = new ConceptController(conceptRepository, null, projectionFactory, null, null, null);
    }

    @After
    public void tearDown() throws Exception {
        resetMock(conceptRepository);
    }

    @Test
    public void getOneForWebByNameShouldHandleHTMLEntities() {
        Concept concept = new Concept();
        concept.setName("AB&CD");
        concept.setDataType("NA");
        when(conceptRepository.findByName("AB&CD")).thenReturn(concept);

        ResponseEntity<ConceptProjection> conceptABandCD = conceptController.getOneForWebByName("AB&amp;CD");

        verify(conceptRepository, times(1)).findByName("AB&CD");
        assertEquals("AB&CD", conceptABandCD.getBody().getName());
        assertEquals("NA", conceptABandCD.getBody().getDataType());
    }
}