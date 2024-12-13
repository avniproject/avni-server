package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.Checklist;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;

public class ChecklistResourceProcessor extends ResourceProcessor<Checklist> {
    @Override
    public EntityModel<Checklist> process(EntityModel<Checklist> resource) {
        Checklist checklist = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(checklist.getProgramEnrolment().getUuid(), "programEnrolmentUUID"));
        resource.add(Link.of(checklist.getChecklistDetail().getUuid(), "checklistDetailUUID"));
        return resource;
    }

}
