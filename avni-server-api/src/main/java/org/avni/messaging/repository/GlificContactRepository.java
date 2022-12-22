package org.avni.messaging.repository;

import org.avni.messaging.contract.glific.GlificContactGroupsResponse;
import org.avni.messaging.contract.glific.GlificGetContactsResponse;
import org.avni.messaging.contract.glific.GlificOptinContactResponse;
import org.avni.messaging.contract.glific.GlificResponse;
import org.avni.messaging.external.GlificRestClient;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;

import java.io.IOException;

@Repository
public class GlificContactRepository extends AbstractGlificRepository {
    private final String OPTIN_CONTACT_JSON;
    private final GlificRestClient glificRestClient;
    private final String GET_CONTACT_JSON;
    private final String GET_CONTACT_GROUP_JSON;

    public GlificContactRepository(GlificRestClient glificRestClient) {
        this.glificRestClient = glificRestClient;
        GET_CONTACT_JSON = getJson("getContact");
        OPTIN_CONTACT_JSON = getJson("optinContact");
        GET_CONTACT_GROUP_JSON = getJson("getContactGroups");
    }

    public String getOrCreateContact(String phoneNumber, String fullName) {
        GlificGetContactsResponse glificContacts = getContact(phoneNumber);
        return glificContacts.getContacts().isEmpty() ?
                createContact(phoneNumber, fullName) :
                glificContacts.getContacts().get(0).getId();
    }

    private String createContact(String phoneNumber, String fullName) {
        final int NO_OF_DIGITS_IN_INDIAN_MOBILE_NO = 10;
        String phoneNoWithCountryCode = "+91" + phoneNumber.substring(phoneNumber.length() - NO_OF_DIGITS_IN_INDIAN_MOBILE_NO);
        String message = OPTIN_CONTACT_JSON.replace("${phoneNumber}", phoneNoWithCountryCode)
                .replace("${fullName}", fullName);
        GlificOptinContactResponse glificOptinContactResponse = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificOptinContactResponse>>() {
        });
        return glificOptinContactResponse.getOptinContact().getContact().getId();
    }

    private GlificGetContactsResponse getContact(String phoneNumber) {
        String message = GET_CONTACT_JSON.replace("${phoneNumber}", phoneNumber);
        return glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificGetContactsResponse>>() {
        });
    }

    public GlificContactGroupsResponse getContactGroups() {
        return glificRestClient.callAPI(GET_CONTACT_GROUP_JSON, new ParameterizedTypeReference<GlificResponse<GlificContactGroupsResponse>>() {
        });
    }
}
