package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.GroupSubject;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;

public class GroupSubjectResourceProcessor extends ResourceProcessor<GroupSubject>{
    @Override
    public Resource<GroupSubject> process(Resource<GroupSubject> resource) {
        GroupSubject groupSubject = resource.getContent();
        resource.removeLinks();
        resource.add(new Link(groupSubject.getGroupSubject().getUuid(), "groupSubjectUUID"));
        resource.add(new Link(groupSubject.getMemberSubject().getUuid(), "memberSubjectUUID"));
        resource.add(new Link(groupSubject.getGroupRole().getUuid(), "groupRoleUUID"));
        addAuditFields(groupSubject, resource);
        return resource;
    }}
