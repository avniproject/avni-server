package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeactivateMetabaseUserResponse {

    @JsonProperty("success")
    private boolean success;

    public boolean isSuccess() {
        return success;
    }
}
