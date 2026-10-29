package org.avni.server.web.response;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.factory.AddressLevelBuilder;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.ConceptService;
import org.avni.server.service.S3Service;
import org.avni.server.web.request.api.SubjectResponseOptions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SubjectResponseUnitTest {
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptService conceptService;
    @Mock
    private S3Service s3Service;

    private Concept pinCode;

    @Before
    public void setup() {
        initMocks(this);
        pinCode = new ConceptBuilder().withName("Pin code").withDataType(ConceptDataType.Text).build();
        when(conceptRepository.findByUuid(pinCode.getUuid())).thenReturn(pinCode);
        when(conceptService.getObservationValue(any(Concept.class), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    public void shouldIncludeLocationPropertiesOfSubjectLocation() {
        Individual subject = subjectIn(locationWithProperties(new ObservationCollectionBuilder().addObservation(pinCode, "411001").build()));

        SubjectResponse response = SubjectResponse.fromSubject(subject, SubjectResponseOptions.forSingleSubjectWithLocationProperties(false), conceptRepository, conceptService, s3Service);

        assertEquals(Map.of("Pin code", "411001"), response.get("Location Properties"));
    }

    @Test
    public void shouldNotIncludeLocationPropertiesWhenNotRequested() {
        Individual subject = subjectIn(locationWithProperties(new ObservationCollectionBuilder().addObservation(pinCode, "411001").build()));

        SubjectResponse response = SubjectResponse.fromSubject(subject, SubjectResponseOptions.forSingleSubject(false), conceptRepository, conceptService, s3Service);

        assertFalse(response.containsKey("Location Properties"));
    }

    @Test
    public void shouldNotIncludeLocationPropertiesWhenLocationHasNone() {
        SubjectResponseOptions options = SubjectResponseOptions.forSingleSubjectWithLocationProperties(false);

        assertFalse(SubjectResponse.fromSubject(subjectIn(locationWithProperties(null)), options, conceptRepository, conceptService, s3Service).containsKey("Location Properties"));
        assertFalse(SubjectResponse.fromSubject(subjectIn(locationWithProperties(new ObservationCollection())), options, conceptRepository, conceptService, s3Service).containsKey("Location Properties"));
    }

    @Test
    public void shouldNotIncludeLocationPropertiesWhenSubjectHasNoLocation() {
        SubjectResponse response = SubjectResponse.fromSubject(subjectIn(null), SubjectResponseOptions.forSingleSubjectWithLocationProperties(false), conceptRepository, conceptService, s3Service);

        assertFalse(response.containsKey("Location Properties"));
    }

    private AddressLevel locationWithProperties(ObservationCollection locationProperties) {
        AddressLevel addressLevel = new AddressLevelBuilder().withDefaultValuesForNewEntity().type(new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build()).build();
        addressLevel.setLocationProperties(locationProperties);
        return addressLevel;
    }

    private Individual subjectIn(AddressLevel addressLevel) {
        return new SubjectBuilder().withMandatoryFieldsForNewEntity()
                .withSubjectType(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build())
                .withAuditUser(new UserBuilder().userName("user@org").build())
                .withLocation(addressLevel)
                .build();
    }
}
