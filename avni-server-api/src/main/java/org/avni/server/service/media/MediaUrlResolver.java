package org.avni.server.service.media;

import java.util.Optional;

public interface MediaUrlResolver {
    Optional<String> resolve(String url);
}
