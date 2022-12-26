package org.avni.messaging.repository;

import org.avni.messaging.contract.glific.*;
import org.avni.messaging.external.GlificRestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class GlificContactRepository extends AbstractGlificRepository {
    private final String OPTIN_CONTACT_JSON;
    private final GlificRestClient glificRestClient;
    private final String GET_CONTACT_JSON;
    private final String GET_CONTACT_GROUP_JSON;
    private final String GET_CONTACT_GROUP_COUNT_JSON;

    public GlificContactRepository(GlificRestClient glificRestClient) {
        this.glificRestClient = glificRestClient;
        GET_CONTACT_JSON = getJson("getContact");
        OPTIN_CONTACT_JSON = getJson("optinContact");
        GET_CONTACT_GROUP_JSON = getJson("getContactGroups");
        GET_CONTACT_GROUP_COUNT_JSON = getJson("getContactGroupCount");
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

    public GlificContactGroupsResponse getContactGroups(Pageable pageable) {
        String message = GET_CONTACT_GROUP_JSON.replace("\"${offset}\"", Long.toString(pageable.getOffset()))
                .replace("\"${limit}\"", Integer.toString(pageable.getPageSize()));
        return glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificContactGroupsResponse>>() {
        });
    }

    public int getContactGroupCount() {
        GlificContactGroupCountResponse response = glificRestClient.callAPI(GET_CONTACT_GROUP_COUNT_JSON, new ParameterizedTypeReference<GlificResponse<GlificContactGroupCountResponse>>() {
        });
        return response.getCountGroups();
    }
}
