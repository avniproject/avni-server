package org.avni.server.web.task;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.TaskTypeService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.response.AvniEntityResponse;
import org.avni.server.web.request.webapp.task.TaskTypeContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaskTypeWebController {
    private final TaskTypeService taskTypeService;
    private final AccessControlService accessControlService;

    @Autowired
    public TaskTypeWebController(TaskTypeService taskTypeService, AccessControlService accessControlService) {
        this.taskTypeService = taskTypeService;
        this.accessControlService = accessControlService;
    }

    @PostMapping(value = "/web/taskTypes")
    @Transactional
    public void post(@RequestBody List<TaskTypeContract> requests) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        requests.forEach(this::post);
    }

    @PostMapping(value = "/web/taskType")
    @Transactional
    public AvniEntityResponse post(@RequestBody TaskTypeContract request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return new AvniEntityResponse(taskTypeService.saveTaskType(request));
    }

}
