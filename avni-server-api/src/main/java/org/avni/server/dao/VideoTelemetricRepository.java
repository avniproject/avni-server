package org.avni.server.dao;

import org.avni.server.domain.VideoTelemetric;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(collectionResourceRel = "videotelemetric", path = "videotelemetric", exported = false)
public interface VideoTelemetricRepository extends AvniJpaRepository<VideoTelemetric, Long> {
    VideoTelemetric findByUuid(String uuid);
}
