package org.avni.server.web.response;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which tier of the fast-sync preference order an artifact came from. This is part of the wire
 * contract because the client cannot infer it safely: a catchment artifact was uploaded by a peer
 * and carries that peer's identity, so the client overwrites it on restore, while the other two are
 * generated for one user and the client asserts on the identity instead. Getting that backwards
 * either rejects every catchment dump or silently accepts a mis-keyed one.
 */
public enum FastSyncTier {
    PER_USER("perUser"),
    CATCHMENT("catchment"),
    SNAPSHOT("snapshot");

    private final String wireName;

    FastSyncTier(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String getWireName() {
        return wireName;
    }
}
