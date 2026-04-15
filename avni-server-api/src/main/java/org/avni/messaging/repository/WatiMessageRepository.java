package org.avni.messaging.repository;

import org.avni.messaging.contract.wati.WatiApiResponse;
import org.avni.messaging.contract.wati.WatiParameter;
import org.avni.messaging.contract.wati.WatiReceiver;
import org.avni.messaging.contract.wati.WatiTemplateMessageRequest;
import org.avni.messaging.domain.WatiSystemConfig;
import org.avni.messaging.domain.exception.WatiConnectException;
import org.avni.messaging.external.WatiRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sends WhatsApp template messages via the Wati API.
 *
 * API used: POST /api/v1/sendTemplateMessages
 * Working request format (confirmed via Postman):
 * {
 *   "template_name": "chlorine_refill_reminder",
 *   "broadcast_name": "test",
 *   "receivers": [
 *     {
 *       "whatsappNumber": "919039903650",
 *       "customParams": [{"name": "1", "value": "Nupoor"}]
 *     }
 *   ]
 * }
 */
@Repository
@Lazy
public class WatiMessageRepository {

    private static final Logger logger = LoggerFactory.getLogger(WatiMessageRepository.class);

    private final WatiRestClient watiRestClient;

    public WatiMessageRepository(WatiRestClient watiRestClient) {
        this.watiRestClient = watiRestClient;
    }

    /**
     * Sends a Wati template message to the given phone number.
     *
     * @param templateName  Wati template name (must match approved template on Wati dashboard)
     * @param phoneNumber   E.164 without '+' format e.g. "919876543210" (stored as MessageReceiver.externalId)
     * @param paramValues   Ordered parameter values — zipped with template param names from WatiSystemConfig
     */
    public void sendTemplateMessage(String templateName, String phoneNumber, String[] paramValues) {
        WatiSystemConfig config = watiRestClient.getSystemConfig();
        List<WatiParameter> customParams = buildParameters(templateName, paramValues, config);

        WatiReceiver receiver = new WatiReceiver(phoneNumber, customParams);
        WatiTemplateMessageRequest request = new WatiTemplateMessageRequest(
                templateName,
                templateName, // broadcast_name: use template name as label for Wati's broadcast tracking
                Collections.singletonList(receiver)
        );

        logger.info("Sending Wati template '{}' to phone number {}", templateName, phoneNumber);
        WatiApiResponse response = watiRestClient.post(
                "/api/v1/sendTemplateMessages",
                request,
                WatiApiResponse.class
        );

        if (response == null || !response.isResult()) {
            String info = response != null ? response.getInfo() : "null response";
            logger.error("Wati template '{}' send failed for {}: {}", templateName, phoneNumber, info);
            throw new WatiConnectException("Wati template send failed: " + info);
        }

        logger.info("Wati template '{}' sent successfully to {}", templateName, phoneNumber);
    }

    /**
     * Builds the customParams list for a receiver.
     * For positional variables ({{1}}, {{2}}), param name is "1", "2", etc.
     * Names can be configured per-template in WatiSystemConfig.templateParamNames.
     */
    private List<WatiParameter> buildParameters(String templateName, String[] paramValues, WatiSystemConfig config) {
        List<String> paramNames = config.getTemplateParamNames(templateName);
        List<WatiParameter> parameters = new ArrayList<>();

        for (int i = 0; i < paramValues.length; i++) {
            String name = (paramNames != null && i < paramNames.size())
                    ? paramNames.get(i)
                    : String.valueOf(i + 1); // fallback: "1", "2", ... for {{1}}, {{2}}
            parameters.add(new WatiParameter(name, paramValues[i]));
        }

        return parameters;
    }
}
