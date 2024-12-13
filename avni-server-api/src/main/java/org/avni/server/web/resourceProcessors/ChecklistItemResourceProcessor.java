package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.ChecklistItem;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;

public class ChecklistItemResourceProcessor extends ResourceProcessor<ChecklistItem>{
    @Override
    public EntityModel<ChecklistItem> process(EntityModel<ChecklistItem> resource) {
        ChecklistItem checklistItem = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(checklistItem.getChecklist().getUuid(), "checklistUUID"));
        resource.add(Link.of(checklistItem.getChecklistItemDetail().getUuid(), "checklistItemDetailUUID"));
        return resource;
    }
}
