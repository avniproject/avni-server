package org.avni.server.web.response.customimpl;

public record CatchmentLocationNode(
        String uuid,
        String name,
        String type,
        String parentUuid
) {}
