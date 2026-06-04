package org.avni.server.service;

import org.avni.server.common.BulkItemSaveException;
import org.avni.server.common.EntityHelper;
import org.avni.server.dao.task.TaskStatusRepository;
import org.avni.server.dao.task.TaskTypeRepository;
import org.avni.server.domain.task.TaskStatus;
import org.avni.server.domain.task.TaskType;
import org.avni.server.web.request.webapp.task.TaskStatusContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskStatusService implements NonScopeAwareService {

    private final TaskStatusRepository taskStatusRepository;
    private final TaskTypeRepository taskTypeRepository;

    @Autowired
    public TaskStatusService(TaskStatusRepository taskStatusRepository, TaskTypeRepository taskTypeRepository) {
        this.taskStatusRepository = taskStatusRepository;
        this.taskTypeRepository = taskTypeRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return taskStatusRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public List<TaskStatusContract> getAll() {
        return taskStatusRepository.findAll()
                .stream()
                .map(TaskStatusContract::fromEntity)
                .collect(Collectors.toList());
    }

    public TaskStatus saveTaskStatus(TaskStatusContract request) {
        TaskStatus taskStatus = buildTaskStatus(request);
        Long taskTypeId = request.getTaskTypeId();
        if (taskTypeId != null) {
            taskStatus.setTaskType(taskTypeRepository.findById(taskTypeId.longValue()));
        }
        taskStatusRepository.save(taskStatus);
        return taskStatus;
    }

    public TaskStatus importTaskStatus(TaskStatusContract request) {
        TaskStatus taskStatus = buildTaskStatus(request);
        TaskType taskType = request.getTaskTypeUUID() == null ? null : taskTypeRepository.findByUuid(request.getTaskTypeUUID());
        if (taskType == null && request.getTaskTypeId() != null) {
            // legacy bundles may carry only the source org's numeric taskTypeId
            taskType = taskTypeRepository.findById(request.getTaskTypeId().longValue());
        }
        taskStatus.setTaskType(taskType);
        taskStatusRepository.save(taskStatus);
        return taskStatus;
    }

    private TaskStatus buildTaskStatus(TaskStatusContract request) {
        TaskStatus taskStatus = EntityHelper.newOrExistingEntity(taskStatusRepository, request, new TaskStatus());
        taskStatus.setVoided(request.isVoided());
        taskStatus.setName(request.getName());
        taskStatus.setTerminal(request.isTerminal());
        taskStatus.assignUUIDIfRequired();
        return taskStatus;
    }

    public void saveTaskStatuses(TaskStatusContract[] taskStatusContracts) {
        for (TaskStatusContract taskStatusContract : taskStatusContracts) {
            try {
                // bundle import: the contract's taskTypeId is the source org's primary key,
                // so the task type must be resolved by uuid
                importTaskStatus(taskStatusContract);
            } catch (Exception e) {
                throw new BulkItemSaveException(taskStatusContract, e);
            }
        }
    }
}
