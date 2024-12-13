package org.avni.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.avni.server.application.KeyType;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.util.Arrays;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@PrepareForTest({UserContextHolder.class})
public class AddressLevelServiceTest {
    @Mock
    private EntityManager entityManager;

    @Before
    public void setUp() {
        entityManager = Mockito.mock(EntityManager.class);
        Query query = Mockito.mock(Query.class);
        when(entityManager.createNativeQuery(Mockito.anyString())).thenReturn(query);
        when(entityManager.createNativeQuery(Mockito.anyString()).executeUpdate()).thenReturn(1);
        Organisation org = mock(Organisation.class);
        UserContext userContext = new UserContext();
        userContext.setOrganisation(org);
        UserContextHolder.create(userContext);
        when(org.getDbUser()).thenReturn("db-user");
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void shouldFetchDifferentAddressIdsWhenSubjectTypeChanges() throws JsonProcessingException {
        LocationRepository locationRepository = mock(LocationRepository.class);
        AddressLevelTypeRepository addressLevelTypeRepository = mock(AddressLevelTypeRepository.class);
        OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);

        when(locationRepository.getCatchmentAddressesForCatchmentIdAndLocationTypeId(1L, Arrays.asList(1L))).thenReturn(asList(
                new CatchmentAddressProjectionTestImplementation(1L,  1L, 1L, 1L),
                new CatchmentAddressProjectionTestImplementation(2L,  2L, 1L, 1L)
        ));

        when(locationRepository.getCatchmentAddressesForCatchmentIdAndLocationTypeId(1L, Arrays.asList(2L))).thenReturn(asList(
                new CatchmentAddressProjectionTestImplementation( 3L, 3L, 1L, 2L),
                new CatchmentAddressProjectionTestImplementation(4L, 4L, 1L, 2L)
        ));

        String orgConfig = "[{\"subjectTypeUUID\": \"first-subject-type-uuid\", \"locationTypeUUIDs\": [\"first-address-level-type-uuid\"]},{\"subjectTypeUUID\": \"second-subject-type-uuid\", \"locationTypeUUIDs\": [\"second-address-level-type-uuid\"]}]";
        when(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())).thenReturn(
                asList(ObjectMapperSingleton.getObjectMapper().readValue(orgConfig, Map[].class))
        );
        when(addressLevelTypeRepository.findAllByUuidIn(singletonList("first-address-level-type-uuid")))
                .thenReturn(singletonList(createAddressLevelType(1L)));
        when(addressLevelTypeRepository.findAllByUuidIn(singletonList("second-address-level-type-uuid")))
                .thenReturn(singletonList(createAddressLevelType(2L)));

        AddressLevelCache addressLevelCache = new AddressLevelCache(entityManager, locationRepository);
        AddressLevelService addressLevelService = new AddressLevelService(locationRepository, addressLevelTypeRepository, organisationConfigService, addressLevelCache, mock(LocationHierarchyService.class));

        Catchment catchment = new Catchment();
        catchment.setId(1L);

        assertThat(addressLevelService.getAllRegistrationAddressIdsBySubjectType(catchment, createSubjectType("first-subject-type-uuid"))).contains(1L, 2L);
        assertThat(addressLevelService.getAllRegistrationAddressIdsBySubjectType(catchment, createSubjectType("second-subject-type-uuid"))).contains(3L, 4L);
    }

    private SubjectType createSubjectType(String uuid) {
        SubjectType subjectType1 = new SubjectType();
        subjectType1.setUuid(uuid);
        return subjectType1;
    }

    private AddressLevelType createAddressLevelType(long id) {
        AddressLevelType addressLevelType = new AddressLevelType();
        addressLevelType.setId(id);
        return addressLevelType;
    }

}
