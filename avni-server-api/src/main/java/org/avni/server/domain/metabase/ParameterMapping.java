package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ParameterMapping {

    @JsonProperty("parameter_id")
    private String parameterId;

    @JsonProperty("card_id")
    private int cardId;

    @JsonProperty("target")
    private ArrayNode target;

    public ParameterMapping(String parameterId, int cardId, Target target) {
        this.parameterId = parameterId;
        this.cardId = cardId;
        this.target = target.toJson();
    }

    public String getParameterId() {
        return parameterId;
    }

    public int getCardId() {
        return cardId;
    }

    public ArrayNode getTarget() {
        return target;
    }
}
