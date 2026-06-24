package org.avni.server.web.response.impl;

public record CatchmentLocationNode(
        String uuid,
        String name,
        String type,
        String parentUuid
) {}
