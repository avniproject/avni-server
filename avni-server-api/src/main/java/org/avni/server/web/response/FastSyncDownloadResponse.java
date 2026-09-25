package org.avni.server.web.response;

import java.util.List;

public record FastSyncDownloadResponse(String url, FastSyncTier tier, List<String> supersededResetSyncUuids) {
}
