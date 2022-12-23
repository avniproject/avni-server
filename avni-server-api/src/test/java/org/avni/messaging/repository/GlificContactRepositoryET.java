package org.avni.messaging.repository;

import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@Sql({"/externalTestDataSensitive.sql"})
public class GlificContactRepositoryET extends AbstractControllerIntegrationTest {
    @Autowired
    private GlificContactRepository glificContactRepository;

    @Test
    public void shouldGetContactGroups() {
        PageRequest pageable = PageRequest.of(0, 25);
        GlificContactGroupsResponse contactGroups = glificContactRepository.getContactGroups(pageable);
        assertThat(contactGroups.getGroups().size()).isGreaterThan(0);
    }
}
