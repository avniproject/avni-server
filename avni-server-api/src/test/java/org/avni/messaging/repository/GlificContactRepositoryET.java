package org.avni.messaging.repository;

import org.avni.messaging.contract.glific.GlificContactGroupContactsResponse;
import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.messaging.contract.glific.GlificGetGroupResponse;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql({"/externalTestDataSensitive.sql"})
public class GlificContactRepositoryET extends AbstractControllerIntegrationTest {
    @Autowired
    private GlificContactRepository glificContactRepository;

    @Test
    public void shouldGetContactGroups() {
        PageRequest pageable = PageRequest.of(0, 5);
        List<GlificContactGroupsResponse.ContactGroup> contactGroups = glificContactRepository.getContactGroups(pageable);
        assertThat(contactGroups.size()).isGreaterThan(0);
    }

    @Test
    public void shouldGetContactGroupCount() {
        int contactGroupCount = glificContactRepository.getContactGroupCount();
        assertThat(contactGroupCount).isGreaterThan(0);
    }

    @Test
    public void shouldGetContactGroupContacts() {
        PageRequest pageable = PageRequest.of(0, 5);
        List<GlificContactGroupContactsResponse.GlificContactGroupContacts> contactGroupContacts = glificContactRepository.getContactGroupContacts("1460" ,pageable);
        assertThat(contactGroupContacts.size()).isGreaterThan(0);
    }

    @Test
    public void shouldGetContactGroupContactsCount() {
        int contactGroupContactCount = glificContactRepository.getContactGroupContactsCount("1460");
        assertThat(contactGroupContactCount).isGreaterThan(0);
    }

    @Test
    public void shouldGetGroup() {
        GlificGetGroupResponse.GlificGroup glificGroup = glificContactRepository.getContactGroup("1460");
        assertThat(glificGroup.getLabel().length()).isGreaterThan(0);
    }
}
