package org.avni.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.KeyType;
import org.avni.server.application.projections.VirtualCatchmentProjection;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.SubjectType;
import org.junit.Test;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AddressLevelServiceTest {

    @Test
    public void shouldFetchDifferentAddressIdsWhenSubjectTypeChanges() throws JsonProcessingException {
        LocationRepository locationRepository = mock(LocationRepository.class);
        AddressLevelTypeRepository addressLevelTypeRepository = mock(AddressLevelTypeRepository.class);
        OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);

        when(locationRepository.getVirtualCatchmentsForCatchmentId(1L)).thenReturn(asList(
                new VirtualCatchmentProjectImplementation(1L, 1L, 1L, 1L),
                new VirtualCatchmentProjectImplementation(2L, 2L, 1L, 1L),
                new VirtualCatchmentProjectImplementation(3L, 3L, 1L, 2L),
                new VirtualCatchmentProjectImplementation(4L, 4L, 1L, 2L)
        ));

        String orgConfig = "[{\"subjectTypeUUID\": \"first-subject-type-uuid\", \"locationTypeUUIDs\": [\"first-address-level-type-uuid\"]},{\"subjectTypeUUID\": \"second-subject-type-uuid\", \"locationTypeUUIDs\": [\"second-address-level-type-uuid\"]}]";
        ObjectMapper objectMapper = new ObjectMapper();
        when(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())).thenReturn(
                asList(objectMapper.readValue(orgConfig, Map[].class))
        );
        when(addressLevelTypeRepository.findAllByUuidIn(singletonList("first-address-level-type-uuid")))
                .thenReturn(singletonList(createAddressLevelType(1L)));
        when(addressLevelTypeRepository.findAllByUuidIn(singletonList("second-address-level-type-uuid")))
                .thenReturn(singletonList(createAddressLevelType(2L)));

        AddressLevelService addressLevelService = new AddressLevelService(locationRepository, addressLevelTypeRepository, organisationConfigService);

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

    public static class VirtualCatchmentProjectImplementation implements VirtualCatchmentProjection {
        private final Long id;
        private final Long addressLevel_id;
        private final Long catchment_id;
        private final Long type_id;

        public VirtualCatchmentProjectImplementation(Long id, Long addressLevel_id, Long catchment_id, Long type_id) {
            this.id = id;
            this.addressLevel_id = addressLevel_id;
            this.catchment_id = catchment_id;
            this.type_id = type_id;
        }

        @Override
        public Long getId() {
            return this.id;
        }

        @Override
        public Long getAddresslevel_id() {
            return this.addressLevel_id;
        }

        @Override
        public Long getCatchment_id() {
            return this.catchment_id;
        }

        @Override
        public Long getType_id() {
            return this.type_id;
        }
    }
}
