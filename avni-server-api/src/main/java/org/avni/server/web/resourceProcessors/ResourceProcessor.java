package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.CHSEntity;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;

public abstract class ResourceProcessor<Entity> {
    /**
     * Individual controllers handle their own resource processing. This is only for backward compatibility. Hence its implementers may not implement resource processing for all the fields
     */
    @Deprecated
    public abstract Resource<Entity> process(Resource<Entity> resource);

    public static void addAuditFields(CHSEntity chsEntity, Resource resource) {
        resource.add(new Link(chsEntity.getCreatedBy().getUuid(), "createdByUUID"));
        resource.add(new Link(chsEntity.getCreatedBy().getName(), "createdBy"));
        resource.add(new Link(chsEntity.getLastModifiedBy().getUuid(), "lastModifiedByUUID"));
        resource.add(new Link(chsEntity.getLastModifiedBy().getName(), "lastModifiedBy"));
    }
}
