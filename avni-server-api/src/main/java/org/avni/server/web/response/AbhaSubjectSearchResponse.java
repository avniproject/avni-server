package org.avni.server.web.response;

import java.util.List;

// The UUIDs of the live subjects in the organisation holding an ABHA number, and nothing else about them.
public record AbhaSubjectSearchResponse(List<String> subjectUUIDs) {
}
