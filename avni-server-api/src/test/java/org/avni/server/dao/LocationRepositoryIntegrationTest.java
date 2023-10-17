package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.factory.AddressLevelTypeBuilder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql({"/test-data.sql"})
public class LocationRepositoryIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private LocationRepository locationRepository;

    @Test
    public void testFindByTitleAndAddressLevelType() {
        AddressLevel addressLevel = locationRepository.findById(1L).get();
        List<AddressLevel> addressLevels = locationRepository.findByTitleAndType(addressLevel.getTitle(), addressLevel.getType(), PageRequest.of(0, 1));
        assertThat(addressLevels.size()).isEqualTo(1);

        addressLevels = locationRepository.findByTitleAndType("non-existent address", addressLevel.getType(), PageRequest.of(0, 1));
        assertThat(addressLevels.size()).isEqualTo(0);
    }
}
