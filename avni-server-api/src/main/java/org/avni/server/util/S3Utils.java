package org.avni.server.util;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptMedia;
import org.avni.server.web.request.ConceptContract;

import java.util.ArrayList;
import java.util.List;

public class S3Utils {

    public static String extractFileNameFromUrl(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            return null;
        }
        
        if (mediaUrl.contains("/")) {
            String[] parts = mediaUrl.split("/");
            return parts[parts.length - 1].split("\\?")[0];
        }
        
        return mediaUrl;
    }

    public static List<String> getUrlsToDelete(Concept concept, ConceptContract conceptRequest, boolean ignoreConceptRequestMediaAbsence) {
        List<String> oldMediaUrls = concept.getMedia() != null ?
            concept.getMedia().stream()
                .map(ConceptMedia::getUrl)
                .filter(url -> url != null && !url.trim().isEmpty())
                .toList() : new ArrayList<>();
        
        if (conceptRequest.getMedia() != null) {
            List<String> newMediaUrls = conceptRequest.getMedia().stream()
                .map(ConceptMedia::getUrl)
                .filter(url -> url != null && !url.trim().isEmpty())
                .toList();
            
            return oldMediaUrls.stream()
                .filter(oldUrl -> !newMediaUrls.contains(oldUrl))
                .toList();
        } else {
            if (!ignoreConceptRequestMediaAbsence) {
                return oldMediaUrls;
            } else {
                return new ArrayList<>();
            }
        }
    }
}