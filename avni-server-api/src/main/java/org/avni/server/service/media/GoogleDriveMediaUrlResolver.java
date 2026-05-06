package org.avni.server.service.media;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GoogleDriveMediaUrlResolver implements MediaUrlResolver {
    private static final Pattern FILE_PATH_ID = Pattern.compile("https?://drive\\.google\\.com/file/d/([^/?#]+)");
    private static final Pattern QUERY_ID = Pattern.compile("https?://drive\\.google\\.com/(?:open|uc)\\?(?:[^#]*&)?id=([^&#]+)");

    @Override
    public Optional<String> resolve(String url) {
        if (url == null) return Optional.empty();
        String fileId = extractFileId(url);
        if (fileId == null) return Optional.empty();
        return Optional.of("https://drive.google.com/uc?export=download&id=" + fileId);
    }

    private String extractFileId(String url) {
        Matcher matcher = FILE_PATH_ID.matcher(url);
        if (matcher.find()) return matcher.group(1);
        matcher = QUERY_ID.matcher(url);
        if (matcher.find()) return matcher.group(1);
        return null;
    }
}
