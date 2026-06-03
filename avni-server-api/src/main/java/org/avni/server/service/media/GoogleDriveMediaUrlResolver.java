package org.avni.server.service.media;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GoogleDriveMediaUrlResolver implements MediaUrlResolver {
    private static final Pattern DRIVE_FILE_PATH_ID = Pattern.compile("https?://drive\\.google\\.com/(?:u/\\d+/)?file/d/([^/?#]+)");
    private static final Pattern DRIVE_QUERY_ID = Pattern.compile("https?://drive\\.google\\.com/(?:u/\\d+/)?(?:open|uc)\\?(?:[^#]*&)?id=([^&#]+)");
    private static final Pattern DOCS_PATH_ID = Pattern.compile("https?://docs\\.google\\.com/(?:u/\\d+/)?(document|spreadsheets|presentation|drawings)/d/([^/?#]+)");

    private static final Map<String, String> DOCS_EXPORT_FORMAT = Map.of(
            "document", "docx",
            "spreadsheets", "xlsx",
            "presentation", "pptx",
            "drawings", "png"
    );

    @Override
    public Optional<String> resolve(String url) {
        if (url == null) return Optional.empty();

        Matcher matcher = DRIVE_FILE_PATH_ID.matcher(url);
        if (matcher.find()) return Optional.of(driveDownloadUrl(matcher.group(1)));

        matcher = DRIVE_QUERY_ID.matcher(url);
        if (matcher.find()) return Optional.of(driveDownloadUrl(matcher.group(1)));

        matcher = DOCS_PATH_ID.matcher(url);
        if (matcher.find()) {
            String docType = matcher.group(1);
            String fileId = matcher.group(2);
            String format = DOCS_EXPORT_FORMAT.get(docType);
            if (format == null) return Optional.empty();
            return Optional.of(String.format("https://docs.google.com/%s/d/%s/export?format=%s", docType, fileId, format));
        }

        return Optional.empty();
    }

    private String driveDownloadUrl(String fileId) {
        return "https://drive.google.com/uc?export=download&id=" + fileId;
    }
}
