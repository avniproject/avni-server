package org.avni.server.web.response.customimpl;

import java.util.List;

public record CatchmentLocationsResponse(
        List<CatchmentLocationNode> nodes,
        List<String> rootUuids
) {}
