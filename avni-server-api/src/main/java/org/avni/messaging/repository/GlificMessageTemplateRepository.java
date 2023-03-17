package org.avni.messaging.repository;

import org.avni.messaging.contract.glific.GlificMessageTemplate;
import org.avni.messaging.contract.glific.GlificMessageTemplateResponse;
import org.avni.messaging.contract.glific.GlificResponse;
import org.avni.messaging.external.GlificRestClient;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;


@Repository
public class GlificMessageTemplateRepository {
    public static final String GLIFIC_TEMPLATES_FOR_ORG = "glificTemplatesForOrg";

    private final GlificRestClient glificRestClient;
    private final Object MESSAGE_TEMPLATE_REQUEST;

    public GlificMessageTemplateRepository(GlificRestClient glificRestClient) {
        this.glificRestClient = glificRestClient;
        try {
            MESSAGE_TEMPLATE_REQUEST = ObjectMapperSingleton.getObjectMapper().readValue(this.getClass().getResource("/external/glific/messageTemplateRequest.json"), Object.class);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    public List<GlificMessageTemplate> findAll() {
        return glificRestClient.callAPI(MESSAGE_TEMPLATE_REQUEST, new ParameterizedTypeReference<GlificResponse<GlificMessageTemplateResponse>>() {
        }).getSessionTemplates();
    }

    @Cacheable(value = GLIFIC_TEMPLATES_FOR_ORG)
    public List<GlificMessageTemplate> findAllForOrganisationId(Long organisationId) {
        return glificRestClient.callAPI(MESSAGE_TEMPLATE_REQUEST, new ParameterizedTypeReference<GlificResponse<GlificMessageTemplateResponse>>() {
        }).getSessionTemplates();
    }
}
