package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.User;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.EntityModel;

public abstract class ResourceProcessor<Entity> {
    /**
     * Individual controllers handle their own resource processing. This is only for backward compatibility. Hence its implementers may not implement resource processing for all the fields
     */
    @Deprecated
    public abstract EntityModel<Entity> process(EntityModel<Entity> resource);

    public static void addAuditFields(CHSEntity chsEntity, EntityModel resource) {
        addUserFields(chsEntity.getCreatedBy(), resource, "createdBy");
        addUserFields(chsEntity.getLastModifiedBy(), resource, "lastModifiedBy");
    }

    public static void addUserFields(User user, EntityModel resource, String fieldName) {
        if (user == null) return;

        resource.add(Link.of(user.getUuid(), fieldName + "UUID"));
        resource.add(Link.of(user.getName(), fieldName));
    }
}
