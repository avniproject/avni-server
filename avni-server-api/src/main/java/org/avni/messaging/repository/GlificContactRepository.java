package org.avni.messaging.repository;

import org.avni.messaging.contract.ContactGroupRequest;
import org.avni.messaging.contract.glific.*;
import org.avni.messaging.domain.exception.GlificContactNotFoundError;
import org.avni.messaging.domain.exception.GlificException;
import org.avni.messaging.external.GlificRestClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Repository
@Lazy //for better startup performance
public class GlificContactRepository extends AbstractGlificRepository {
    public static final String GLIFIC_CONTACT_FOR_PHONE_NUMBER = "glificContactForPhoneNumber";
    public static final String INDIA_ISD_CODE = "+91";
    public static final String PHONE_NUMBER = "${phoneNumber}";
    public static final String RECEIVER_NAME = "${receiverName}";
    public static final String RECEIVER_ID = "${receiverId}";
    public static final String FULL_NAME = "${fullName}";
    public static final String GROUP_ID = "${groupId}";
    public static final String ID = "${id}";

    private final String OPTIN_CONTACT_JSON;
    private final GlificRestClient glificRestClient;
    private final String GET_CONTACT_JSON;
    private final String GET_CONTACT_GROUPS_JSON;
    private final String GET_CONTACT_GROUP_COUNT_JSON;
    private final String GET_CONTACT_GROUP_CONTACTS_JSON;
    private final String GET_CONTACT_GROUP_CONTACT_COUNT_JSON;
    private final String GET_CONTACT_GROUP_JSON;
    private final String GET_ALL_MSGS_JSON;
    private final String GET_ALL_GROUP_CONVERSATION_MSGS_JSON;
    private final String ADD_CONTACT_TO_GROUP_JSON;
    private final String ADD_CONTACT_GROUP_JSON;
    private final String UPDATE_CONTACT_GROUP_JSON;

    private final static int NO_OF_DIGITS_IN_INDIAN_MOBILE_NO = 10;

    public GlificContactRepository(GlificRestClient glificRestClient) {
        this.glificRestClient = glificRestClient;
        GET_CONTACT_JSON = getJson("getContact");
        OPTIN_CONTACT_JSON = getJson("optinContact");
        GET_CONTACT_GROUPS_JSON = getJson("getContactGroups");
        GET_CONTACT_GROUP_COUNT_JSON = getJson("getContactGroupCount");
        GET_CONTACT_GROUP_CONTACTS_JSON = getJson("getContactGroupContacts");
        GET_CONTACT_GROUP_CONTACT_COUNT_JSON = getJson("getContactGroupContactCount");
        GET_CONTACT_GROUP_JSON = getJson("getContactGroup");
        GET_ALL_MSGS_JSON = getJson("getAllMessages");
        GET_ALL_GROUP_CONVERSATION_MSGS_JSON = getJson("searchConversationMessages");
        ADD_CONTACT_TO_GROUP_JSON = getJson("addContactToGroup");
        ADD_CONTACT_GROUP_JSON = getJson("addContactGroup");
        UPDATE_CONTACT_GROUP_JSON = getJson("updateContactGroup");
    }

    public String getOrCreateContact(String phoneNumber, String fullName) {
        GlificGetContactsResponse glificContacts = getContact(phoneNumber);
        return glificContacts.getContacts().isEmpty() ?
                createContact(phoneNumber, fullName) :
                glificContacts.getContacts().get(0).getId();
    }

    private String createContact(String phoneNumber, String fullName) {
        String phoneNoWithCountryCode = INDIA_ISD_CODE + phoneNumber.substring(phoneNumber.length() - NO_OF_DIGITS_IN_INDIAN_MOBILE_NO);
        String message = OPTIN_CONTACT_JSON.replace(PHONE_NUMBER, phoneNoWithCountryCode)
                .replace(FULL_NAME, fullName);
        GlificOptinContactResponse glificOptinContactResponse = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificOptinContactResponse>>() {
        });
        return glificOptinContactResponse.getOptinContact().getContact().getId();
    }

    private GlificGetContactsResponse getContact(String phoneNumber) {
        String message = GET_CONTACT_JSON.replace(PHONE_NUMBER, phoneNumber);
        return glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificGetContactsResponse>>() {
        });
    }

    public List<GlificContactGroupsResponse.ContactGroup> getContactGroups(Pageable pageable) {
        String message = this.populatePaginationDetails(GET_CONTACT_GROUPS_JSON, pageable);
        GlificContactGroupsResponse glificContactGroupsResponse = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificContactGroupsResponse>>() {
        });
        return glificContactGroupsResponse.getGroups();
    }

    public int getContactGroupCount() {
        GlificContactGroupCountResponse response = glificRestClient.callAPI(GET_CONTACT_GROUP_COUNT_JSON, new ParameterizedTypeReference<GlificResponse<GlificContactGroupCountResponse>>() {
        });
        return response.getCountGroups();
    }

    public List<GlificContactGroupContactsResponse.GlificContactGroupContacts> getContactGroupContacts(String contactGroupId, Pageable pageable) {
        String message = this.populatePaginationDetails(GET_CONTACT_GROUP_CONTACTS_JSON, pageable);
        message = message.replace(GROUP_ID, contactGroupId);
        GlificContactGroupContactsResponse response = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificContactGroupContactsResponse>>() {
        });
        return response.getContacts();
    }

    public int getContactGroupContactsCount(String contactGroupId) {
        String message = GET_CONTACT_GROUP_CONTACT_COUNT_JSON.replace(GROUP_ID, contactGroupId);
        GlificContactGroupContactCountResponse response = glificRestClient.callAPI(message, new
                ParameterizedTypeReference<GlificResponse<GlificContactGroupContactCountResponse>>() {
        });
        return response.getCountContacts();
    }

    public GlificGetGroupResponse.GlificGroup getContactGroup(String id) {
        String message = GET_CONTACT_GROUP_JSON.replace(ID, id);
        GlificGetGroupResponse glificGetGroupResponse = glificRestClient.callAPI(message, new
                ParameterizedTypeReference<GlificResponse<GlificGetGroupResponse>>() {
                });
        return glificGetGroupResponse.getGroup().getGroup();
    }

    /**
     * Find contact matching specified phoneNumber
     * @param phoneNumber
     * @return
     * Throws 404 Not found error, if contact matching specified phoneNumber is not found
     */
    @Cacheable(value = GLIFIC_CONTACT_FOR_PHONE_NUMBER)
    public GlificContactResponse findContact(String phoneNumber) throws GlificContactNotFoundError {
        assert StringUtils.hasText(phoneNumber);
        GlificGetContactsResponse glificContact = getContact(phoneNumber);
        if(glificContact.getContacts().isEmpty()) {
            throw new GlificContactNotFoundError(String.format("Contact with phoneNumber %s not found", phoneNumber));
        }
        return glificContact.getContacts().get(0);
    }

    /**
     * We have got to filter the response received from Glific to only retain the entries that match the receiverId
     *
     * @param phoneNumber
     * @return
     */
    public List<Message> getAllMsgsForContact(String phoneNumber) {
        GlificContactResponse contact = findContact(phoneNumber);
        String getAllMessagesRequest = GET_ALL_MSGS_JSON.replace(RECEIVER_ID, contact.getId());
        GlificSearchDataResponse data = glificRestClient.callAPI(getAllMessagesRequest,
                new ParameterizedTypeReference<GlificResponse<GlificSearchDataResponse>>() {});

        return data.getSearch() != null && !data.getSearch().isEmpty() ?
                data.getSearch().get(0).getMessages() : Collections.emptyList();
    }

    public void addContactToGroup(String contactGroupId, String contactId) {
        String message = ADD_CONTACT_TO_GROUP_JSON.replace("${contactGroupId}", contactGroupId).replace("${contactId}", contactId);
        glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<Object>>() {
        });
    }

    public void createContactGroup(ContactGroupRequest contactGroupRequest) throws GlificException {
        String message = ADD_CONTACT_GROUP_JSON.replace("${contactGroupName}", contactGroupRequest.getLabel())
                .replace("${contactGroupDescription}", contactGroupRequest.getDescription());
        GlificCreateContactGroupResponse glificResponse = glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificCreateContactGroupResponse>>() {
        });
        if (glificResponse.hasErrors()) {
            throw new GlificException(glificResponse.getFirstError());
        }
    }

    public void updateContactGroup(String id, ContactGroupRequest contactGroupRequest) {
        String message = UPDATE_CONTACT_GROUP_JSON.replace("${label}", contactGroupRequest.getLabel())
                .replace("${description}", contactGroupRequest.getDescription())
                .replace("${id}", id);
        glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<Object>>() {
        });
    }
}
