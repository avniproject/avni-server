package org.avni.server.service.abdm;

import org.springframework.http.HttpHeaders;

// DEMO-ONLY abdm-hackathon: never merged.
// The headers of one gateway callback, taken off the request before the work moves to another thread.
public record AbdmCallbackHeaders(String path, String requestId, String timestamp, String hipId, String hiuId, String cmId,
                                  String authorization) {
    public static AbdmCallbackHeaders from(String path, HttpHeaders h) {
        return new AbdmCallbackHeaders(path, h.getFirst(AbdmPaths.H_REQUEST_ID), h.getFirst(AbdmPaths.H_TIMESTAMP),
                h.getFirst(AbdmPaths.H_HIP_ID), h.getFirst(AbdmPaths.H_HIU_ID), h.getFirst(AbdmPaths.H_CM_ID),
                h.getFirst(HttpHeaders.AUTHORIZATION));
    }
}
