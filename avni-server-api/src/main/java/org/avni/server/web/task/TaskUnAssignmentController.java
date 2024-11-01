package org.avni.server.web.task;

import org.avni.server.dao.task.TaskUnAssignmentRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.task.TaskUnAssignment;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.AbstractController;
import org.avni.server.web.RestControllerResourceProcessor;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class TaskUnAssignmentController extends AbstractController<TaskUnAssignment> implements RestControllerResourceProcessor<TaskUnAssignment> {
    private final TaskUnAssignmentRepository taskUnAssignmentRepository;

    @Autowired
    public TaskUnAssignmentController(TaskUnAssignmentRepository taskUnAssignmentRepository) {
        this.taskUnAssignmentRepository = taskUnAssignmentRepository;
    }

    @RequestMapping(value = "/taskUnAssignments", method = RequestMethod.GET)
    @Transactional
    public CollectionModel<EntityModel<TaskUnAssignment>> getTasks(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        User user = UserContextHolder.getUserContext().getUser();
        return wrap(taskUnAssignmentRepository.findByUnassignedUserAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(user, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @RequestMapping(value = "/taskUnAssignments/v2", method = RequestMethod.GET)
    @Transactional
    public SlicedResources<EntityModel<TaskUnAssignment>> getTasksAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        User user = UserContextHolder.getUserContext().getUser();
        return wrap(taskUnAssignmentRepository.findSliceByUnassignedUserAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(user, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @Override
    public EntityModel<TaskUnAssignment> process(EntityModel<TaskUnAssignment> resource) {
        TaskUnAssignment taskUnAssignment = resource.getContent();
        resource.add(Link.of(taskUnAssignment.getTask().getUuid(), "taskUUID"));
        addAuditFields(taskUnAssignment, resource);
        return resource;
    }
}
