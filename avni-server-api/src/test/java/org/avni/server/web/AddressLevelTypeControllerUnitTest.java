package org.avni.server.web;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationMappingRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Organisation;
import org.avni.server.service.LocationService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.ResetSyncService;
import org.avni.server.service.accessControl.AccessControlServiceStub;
import org.avni.server.web.request.AddressLevelTypeContract;
import org.avni.server.web.validation.ValidationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class AddressLevelTypeControllerUnitTest {
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    private LocationService locationService;
    @Mock
    OrganisationRepository organisationRepository;
    @Mock
    LocationMappingRepository locationMappingRepository;
    @Mock
    ResetSyncService resetSyncService;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private ProjectionFactory projectionFactory;

    private AddressLevelTypeController addressLevelTypeController;
    private static final String FOO_UUID = "e8fc567b-6301-4f90-9b5e-349d4d411553";
    private static final String BAR_UUID = "9f339e59-36fb-4533-8bee-fbb2a5a5cf98";

    @Before
    public void setup() {
        initMocks(this);
        locationService = new LocationService(locationRepository, addressLevelTypeRepository, organisationRepository, locationMappingRepository, resetSyncService, organisationConfigService);
        addressLevelTypeController = new AddressLevelTypeController(addressLevelTypeRepository, locationService, projectionFactory, new AccessControlServiceStub(), organisationConfigService);
        AddressLevelType foo = new AddressLevelType();
        foo.setUuid(FOO_UUID);
        foo.setName("foo");
        AddressLevelType bar = new AddressLevelType();
        bar.setId(2L);
        bar.setUuid(BAR_UUID);
        bar.setName("bar");
        when(addressLevelTypeRepository.findByUuid(FOO_UUID)).thenReturn(foo);
        when(addressLevelTypeRepository.findByUuid(BAR_UUID)).thenReturn(bar);
        when(addressLevelTypeRepository.findByNameIgnoreCaseAndIsVoidedFalse("foo")).thenReturn(foo);
        when(addressLevelTypeRepository.findByNameIgnoreCaseAndIsVoidedFalse("FOO")).thenReturn(foo);
        when(addressLevelTypeRepository.findByNameIgnoreCaseAndIsVoidedFalse("Foo")).thenReturn(foo);
        when(addressLevelTypeRepository.findByNameIgnoreCaseAndIsVoidedFalse("bar")).thenReturn(bar);
        when(addressLevelTypeRepository.findByNameIgnoreCaseAndIsVoidedFalse("BAR")).thenReturn(bar);
        when(addressLevelTypeRepository.findByNameIgnoreCaseAndIsVoidedFalse("Bar")).thenReturn(bar);
        when(addressLevelTypeRepository.findOne(1L)).thenReturn(foo);
    }

    @Test()
    public void shouldReturnErrorWhenOnUpdateThereAlreadyExistsLocationTypeWithSameName() throws Exception {
        AddressLevelTypeContract updateAddressLevelType = new AddressLevelTypeContract();
        updateAddressLevelType.setUuid(FOO_UUID);
        updateAddressLevelType.setName("bar");

        ResponseEntity responseEntity = addressLevelTypeController.updateAddressLevelType(1L, updateAddressLevelType);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(400)));
        Map body = (Map) responseEntity.getBody();
        assertThat(body.get("message"), is(equalTo("Location Type with name bar already exists")));
    }

    @Test()
    public void shouldUpdateWhenThereIsNoConflict() throws Exception {
        AddressLevelTypeContract updateAddressLevelType = new AddressLevelTypeContract();
        updateAddressLevelType.setUuid(FOO_UUID);
        updateAddressLevelType.setName("tada");
        updateAddressLevelType.setLevel(1D);

        ResponseEntity responseEntity = addressLevelTypeController.updateAddressLevelType(1L, updateAddressLevelType);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(201)));
    }

    @Test()
    public void shouldReturnErrorWhenOnCreateThereAlreadyExistsLocationTypeWithSameName() throws Exception {
        AddressLevelTypeContract create = new AddressLevelTypeContract();
        create.setUuid(UUID.randomUUID().toString());
        create.setName("foo");

        ResponseEntity responseEntity = addressLevelTypeController.createAddressLevelType(create);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(400)));
        Map body = (Map) responseEntity.getBody();
        assertThat(body.get("message"), is(equalTo("Location Type with name foo already exists")));
    }

    @Test()
    public void shouldCreateWhenThereIsNoConflict() throws Exception {
        AddressLevelTypeContract create = new AddressLevelTypeContract();
        create.setUuid(UUID.randomUUID().toString());
        create.setName("tada");
        create.setLevel(3d);

        ResponseEntity responseEntity = addressLevelTypeController.createAddressLevelType(create);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(201)));
    }

    @Test()
    public void shouldReturnErrorWhenOnCreateThereAlreadyExistsLocationTypeWithSameNameDifferentCase() throws Exception {
        AddressLevelTypeContract create = new AddressLevelTypeContract();
        create.setUuid(UUID.randomUUID().toString());
        create.setName("FOO"); // Different case from existing "foo"

        ResponseEntity responseEntity = addressLevelTypeController.createAddressLevelType(create);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(400)));
        Map body = (Map) responseEntity.getBody();
        assertThat(body.get("message"), is(equalTo("Location Type with name FOO already exists")));
    }

    @Test()
    public void shouldReturnErrorWhenBatchSaveContainsDuplicateNames() throws Exception {
        AddressLevelTypeContract validContract = new AddressLevelTypeContract();
        validContract.setUuid(UUID.randomUUID().toString());
        validContract.setName("valid");
        validContract.setLevel(2d);

        AddressLevelTypeContract duplicateContract = new AddressLevelTypeContract();
        duplicateContract.setUuid(UUID.randomUUID().toString());
        duplicateContract.setName("bar"); // Already exists
        duplicateContract.setLevel(1d);

        ResponseEntity responseEntity = addressLevelTypeController.save(java.util.Arrays.asList(validContract, duplicateContract));
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(400)));
        Map body = (Map) responseEntity.getBody();
        assertThat(body.get("message"), is(equalTo("Location Type with name bar already exists")));
    }

    @Test
    public void shouldUpdateWhenNameChangedWithDifferentCase() throws Exception {
        AddressLevelTypeContract updateAddressLevelType = new AddressLevelTypeContract();
        updateAddressLevelType.setUuid(FOO_UUID);
        updateAddressLevelType.setName("FOO"); // Same name as original but different case
        updateAddressLevelType.setLevel(1D);

        ResponseEntity responseEntity = addressLevelTypeController.updateAddressLevelType(1L, updateAddressLevelType);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(201)));
    }

    @Test
    public void shouldVoidAddressLevelTypeAndCleanupReferences() throws Exception {
        ResponseEntity responseEntity = addressLevelTypeController.voidAddressLevelType(1L);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(200)));
    }

    @Test(expected = ValidationException.class)
    public void shouldDetectDuplicatesInCreateAddressLevelTypesBatch() throws Exception {
        Organisation testOrg = new Organisation();
        testOrg.setId(1L);

        AddressLevelTypeContract contract1 = new AddressLevelTypeContract();
        contract1.setUuid(UUID.randomUUID().toString());
        contract1.setName("Test Location Type");
        contract1.setLevel(1d);

        AddressLevelTypeContract contract2 = new AddressLevelTypeContract();
        contract2.setUuid(UUID.randomUUID().toString());
        contract2.setName("TEST LOCATION TYPE"); // Same name with different case
        contract2.setLevel(2d);

        AddressLevelTypeContract[] contracts = new AddressLevelTypeContract[]{contract1, contract2};

        // This should throw ValidationException due to case-insensitive name conflict
        locationService.createAddressLevelTypes(testOrg, contracts);
    }
}
