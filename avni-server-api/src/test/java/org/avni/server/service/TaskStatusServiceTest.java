package org.avni.server.service;

import org.avni.server.dao.task.TaskStatusRepository;
import org.avni.server.dao.task.TaskTypeRepository;
import org.avni.server.domain.task.TaskStatus;
import org.avni.server.domain.task.TaskType;
import org.avni.server.web.request.webapp.task.TaskStatusContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class TaskStatusServiceTest {

    @Mock
    private TaskStatusRepository taskStatusRepository;
    @Mock
    private TaskTypeRepository taskTypeRepository;

    private TaskStatusService taskStatusService;

    @Before
    public void setUp() {
        initMocks(this);
        taskStatusService = new TaskStatusService(taskStatusRepository, taskTypeRepository);
    }

    private TaskStatusContract contract(Long taskTypeId, String taskTypeUUID) {
        TaskStatusContract contract = new TaskStatusContract();
        contract.setUuid("task-status-uuid");
        contract.setName("Open");
        contract.setTaskTypeId(taskTypeId);
        contract.setTaskTypeUUID(taskTypeUUID);
        return contract;
    }

    @Test
    public void bundleImportResolvesTaskTypeByUuidNotSourceOrgId() {
        TaskType taskType = new TaskType();
        taskType.setUuid("task-type-uuid");
        when(taskTypeRepository.findByUuid("task-type-uuid")).thenReturn(taskType);

        taskStatusService.saveTaskStatuses(new TaskStatusContract[]{contract(999L, "task-type-uuid")});

        ArgumentCaptor<TaskStatus> captor = ArgumentCaptor.forClass(TaskStatus.class);
        verify(taskStatusRepository).save(captor.capture());
        assertEquals(taskType, captor.getValue().getTaskType());
        verify(taskTypeRepository, never()).findById(anyLong());
    }

    @Test
    public void bundleImportFallsBackToIdWhenUuidMissing() {
        TaskType taskType = new TaskType();
        when(taskTypeRepository.findById(5L)).thenReturn(taskType);

        taskStatusService.saveTaskStatuses(new TaskStatusContract[]{contract(5L, null)});

        ArgumentCaptor<TaskStatus> captor = ArgumentCaptor.forClass(TaskStatus.class);
        verify(taskStatusRepository).save(captor.capture());
        assertEquals(taskType, captor.getValue().getTaskType());
    }
}
