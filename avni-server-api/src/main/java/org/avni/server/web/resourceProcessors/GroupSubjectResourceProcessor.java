package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.GroupSubject;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.EntityModel;

public class GroupSubjectResourceProcessor extends ResourceProcessor<GroupSubject>{
    @Override
    public EntityModel<GroupSubject> process(EntityModel<GroupSubject> resource) {
        GroupSubject groupSubject = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(groupSubject.getGroupSubject().getUuid(), "groupSubjectUUID"));
        resource.add(Link.of(groupSubject.getMemberSubject().getUuid(), "memberSubjectUUID"));
        resource.add(Link.of(groupSubject.getGroupRole().getUuid(), "groupRoleUUID"));
        return resource;
    }}
