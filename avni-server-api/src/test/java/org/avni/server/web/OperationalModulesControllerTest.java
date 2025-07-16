package org.avni.server.web;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.SubjectTypeSetting;
import org.avni.server.web.request.AddressLevelTypeContract;
import org.avni.server.web.request.CustomRegistrationLocationTypeContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class OperationalModulesControllerTest {

    private OperationalModulesController controller;

    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new OperationalModulesController(null, null, null, null, null,
                addressLevelTypeRepository, null, null, null, null);
    }

    @Test
    public void shouldFilterVoidedAddressLevelTypesInCustomRegistrationLocations() {
        // Setup
        SubjectTypeSetting setting = new SubjectTypeSetting();
        setting.setSubjectTypeUUID("test-subject-type-uuid");

        String activeTypeUuid = UUID.randomUUID().toString();
        String voidedTypeUuid = UUID.randomUUID().toString();
        String nullTypeUuid = UUID.randomUUID().toString();

        setting.setLocationTypeUUIDs(Arrays.asList(activeTypeUuid, voidedTypeUuid, nullTypeUuid));

        // Create mock address level types
        AddressLevelType activeType = new AddressLevelType();
        activeType.setUuid(activeTypeUuid);
        activeType.setName("Active Type");
        activeType.setVoided(false);

        AddressLevelType voidedType = new AddressLevelType();
        voidedType.setUuid(voidedTypeUuid);
        voidedType.setName("Voided Type");
        voidedType.setVoided(true);

        // Setup repository mock
        when(addressLevelTypeRepository.findByUuid(activeTypeUuid)).thenReturn(activeType);
        when(addressLevelTypeRepository.findByUuid(voidedTypeUuid)).thenReturn(voidedType);
        when(addressLevelTypeRepository.findByUuid(nullTypeUuid)).thenReturn(null);

        // Use reflection to access the private method
        CustomRegistrationLocationTypeContract result =
                (CustomRegistrationLocationTypeContract) ReflectionTestUtils.invokeMethod(
                        controller,
                        "getCustomRegistrationLocationTypeContract",
                        setting
                );

        // Verify
        assertEquals("test-subject-type-uuid", result.getSubjectTypeUUID());
        List<AddressLevelTypeContract> addressLevels = result.getAddressLevels();
        assertEquals(1, addressLevels.size());
        assertEquals(activeTypeUuid, addressLevels.get(0).getUuid());
        assertEquals("Active Type", addressLevels.get(0).getName());
    }
}
