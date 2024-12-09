package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificMessageTemplateResponse {
    private List<GlificMessageTemplate> sessionTemplates;

    public List<GlificMessageTemplate> getSessionTemplates() {
        return sessionTemplates;
    }

    public void setSessionTemplates(List<GlificMessageTemplate> sessionTemplates) {
        this.sessionTemplates = sessionTemplates;
    }
}
