package org.avni.server.web.task;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.TaskStatusService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.response.AvniEntityResponse;
import org.avni.server.web.request.webapp.task.TaskStatusContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskStatusWebController {
    private final TaskStatusService taskStatusService;
    private final AccessControlService accessControlService;

    @Autowired
    public TaskStatusWebController(TaskStatusService taskStatusService, AccessControlService accessControlService) {
        this.taskStatusService = taskStatusService;
        this.accessControlService = accessControlService;
    }

    @PostMapping(value = "/web/taskStatus")
    @Transactional
    @ResponseBody
    public AvniEntityResponse post(@RequestBody TaskStatusContract request) {
        accessControlService.checkPrivilege(PrivilegeType.EditTask);
        return new AvniEntityResponse(taskStatusService.saveTaskStatus(request));
    }
}
