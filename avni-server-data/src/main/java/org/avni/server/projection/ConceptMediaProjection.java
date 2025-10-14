package org.avni.server.projection;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.avni.server.domain.ConceptMedia;
import org.springframework.data.rest.core.config.Projection;

@Projection(name = "ConceptMediaProjection", types = {ConceptMedia.class})
public interface ConceptMediaProjection {
    @JsonProperty("url")
    String getUrl();
    
    @JsonProperty("type")
    ConceptMedia.MediaType getType();
}
