package org.avni.messaging.repository;

import org.avni.messaging.contract.glific.GlificMessageResponse;
import org.avni.messaging.contract.glific.GlificResponse;
import org.avni.messaging.external.GlificRestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;

@Repository
public class GlificMessageRepository extends AbstractGlificRepository {
    private final GlificRestClient glificRestClient;
    private final String SEND_HSM_MESSAGE_JSON;

    public GlificMessageRepository(GlificRestClient glificRestClient) {
        this.glificRestClient = glificRestClient;
        SEND_HSM_MESSAGE_JSON = getJson("sendHsmMessage");
    }

    public GlificMessageResponse sendMessageToContact(String messageTemplateId, String externalId, String[] parameters) {
        String message = SEND_HSM_MESSAGE_JSON.replace("\"${templateId}\"", messageTemplateId)
                .replace("\"${receiverId}\"", externalId)
                .replace("\"${parameters}\"", arrayToString(parameters));
        return glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificMessageResponse>>() {
        });
    }

    public void sendMessageToGroup(String externalId, String messageTemplateId, String[] parameters) {
        String message = getJson("sendHsmMessageToGroup").replace("\"${templateId}\"", messageTemplateId)
                .replace("\"${groupId}\"", externalId)
                .replace("\"${parameters}\"", arrayToString(parameters));

        glificRestClient.callAPI(message, new ParameterizedTypeReference<GlificResponse<GlificMessageResponse>>() {});
    }

    private String arrayToString(String[] items) {
        if(items == null || items.length <= 0) return "[]";
        StringBuffer result = new StringBuffer();
        result.append("[");
        for (String item : items) {
            result.append("\"");
            result.append(item);
            result.append("\",");
        }
        result.deleteCharAt(result.length() - 1);
        result.append("]");
        return result.toString();
    }
}
