package org.avni.server.web.response.customimpl;

import java.util.Map;

public record SubjectSummary(
        String uuid,
        String externalId,
        String displayName,
        Map<String, String> location
) {}
