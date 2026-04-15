package org.avni.messaging.contract.wati;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for Wati's send template messages API:
 * POST /api/v1/sendTemplateMessages
 *
 * Working format (confirmed via Postman):
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
public class WatiTemplateMessageRequest {

    @JsonProperty("template_name")
    private String templateName;

    @JsonProperty("broadcast_name")
    private String broadcastName;

    @JsonProperty("receivers")
    private List<WatiReceiver> receivers;

    public WatiTemplateMessageRequest(String templateName, String broadcastName, List<WatiReceiver> receivers) {
        this.templateName = templateName;
        this.broadcastName = broadcastName;
        this.receivers = receivers;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getBroadcastName() {
        return broadcastName;
    }

    public List<WatiReceiver> getReceivers() {
        return receivers;
    }
}
