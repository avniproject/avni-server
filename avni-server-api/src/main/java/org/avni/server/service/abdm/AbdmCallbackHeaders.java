package org.avni.server.service.abdm;

// DEMO-ONLY abdm-hackathon: never merged.
// What the message log keeps of one inbound Eka webhook, taken off the request before the work moves to another thread.
public record AbdmCallbackHeaders(String path, String requestId, String timestamp, String hipId, String hiuId, String cmId,
                                  String authorization) {
}
