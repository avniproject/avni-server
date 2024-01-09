package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.CHSEntity;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;

public abstract class ResourceProcessor<Entity> {
    public abstract Resource<Entity> process(Resource<Entity> resource);

    protected void addAuditFields(CHSEntity chsEntity, Resource<Entity> resource) {
        resource.add(new Link(chsEntity.getCreatedBy().getUuid(), "createdByUUID"));
        resource.add(new Link(chsEntity.getLastModifiedBy().getUuid(), "lastModifiedByUUID"));
    }
}
