package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.Checklist;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;

public class ChecklistResourceProcessor extends ResourceProcessor<Checklist> {
    @Override
    public Resource<Checklist> process(Resource<Checklist> resource) {
        Checklist checklist = resource.getContent();
        resource.removeLinks();
        resource.add(new Link(checklist.getProgramEnrolment().getUuid(), "programEnrolmentUUID"));
        resource.add(new Link(checklist.getChecklistDetail().getUuid(), "checklistDetailUUID"));
        addAuditFields(checklist, resource);
        return resource;
    }

}
