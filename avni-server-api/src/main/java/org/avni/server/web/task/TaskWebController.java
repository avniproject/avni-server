package org.avni.server.web.task;

import jakarta.transaction.Transactional;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.task.Task;
import org.avni.server.service.ConceptService;
import org.avni.server.service.TaskService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.task.TaskAssignmentRequest;
import org.avni.server.web.request.task.TaskFilterCriteria;
import org.avni.server.web.response.Response;
import org.avni.server.web.response.TaskSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TaskWebController {
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;
    private final TaskService taskService;
    private final AccessControlService accessControlService;

    @Autowired
    public TaskWebController(ConceptRepository conceptRepository, ConceptService conceptService, TaskService taskService, AccessControlService accessControlService) {
        this.conceptRepository = conceptRepository;
        this.conceptService = conceptService;
        this.taskService = taskService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/web/task", method = RequestMethod.POST)
    @ResponseBody
    public Page<TaskSearchResponse> getTasks(@RequestBody TaskFilterCriteria filterCriteria, Pageable pageable) {
        accessControlService.checkPrivilege(PrivilegeType.EditTask);
        Page<Task> searchResult = taskService.searchTaskByCriteria(filterCriteria, pageable);
        return searchResult.map(task -> {
            Map<String, Object> metadataMap = new HashMap<>();
            Response.mapObservations(conceptRepository, conceptService, metadataMap, task.getMetadata());
            return TaskSearchResponse.from(task, metadataMap);
        });
    }

    @RequestMapping(value = "/web/taskMetadata", method = RequestMethod.GET)
    public JsonObject getTaskMetadataForSearch() {
        accessControlService.checkPrivilege(PrivilegeType.EditTask);
        return taskService.getTaskMetaData();
    }

    @RequestMapping(value = "/web/taskAssignment", method = RequestMethod.POST)
    @Transactional
    public void taskAssignment(@RequestBody TaskAssignmentRequest taskAssignmentRequest) {
        accessControlService.checkPrivilege(PrivilegeType.EditTask);
        taskService.assignTask(taskAssignmentRequest);
    }
}
