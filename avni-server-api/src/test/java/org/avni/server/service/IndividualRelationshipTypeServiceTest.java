package org.avni.server.service;

import org.avni.server.dao.individualRelationship.IndividualRelationRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipTypeRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class IndividualRelationshipTypeServiceTest {


    IndividualRelationshipTypeService individualRelationshipTypeService;
    @Mock
    IndividualRelationshipTypeRepository individualRelationshipTypeRepository;

    @Mock
    IndividualRelationRepository individualRelationRepository;
    @Before
    public void setUp() {
        initMocks(this);
        individualRelationshipTypeService = new IndividualRelationshipTypeService(individualRelationshipTypeRepository, individualRelationRepository);
    }

    @Test
    public void testGetAllRelationshipTypesShouldReturnVoidedWhenAsked() {
        when(individualRelationshipTypeRepository.findAll()).thenReturn(Collections.emptyList());
        verify(individualRelationshipTypeRepository, times(0)).findAllByIsVoidedFalse();
        individualRelationshipTypeService.getAllRelationshipTypes(true);
    }

    @Test
    public void testGetAllRelationshipTypesShouldNotReturnVoidedWhenAsked() {
        when(individualRelationshipTypeRepository.findAllByIsVoidedFalse()).thenReturn(Collections.emptyList());
        verify(individualRelationshipTypeRepository, times(0)).findAll();
        individualRelationshipTypeService.getAllRelationshipTypes(false);
    }
}