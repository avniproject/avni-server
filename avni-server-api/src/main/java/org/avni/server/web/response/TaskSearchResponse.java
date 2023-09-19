package org.avni.server.web.response;

import org.avni.server.domain.task.Task;
import org.avni.server.util.O;

import java.util.Map;

public class TaskSearchResponse {
    private String name;
    private Long id;
    private String createdDateTime;
    private String scheduledOn;
    private String completedOn;
    private String taskStatus;
    private String assignedTo;
    private String taskType;
    private Map<String, Object> metadata;

    public static TaskSearchResponse from(Task task, Map<String, Object> metadataMap) {
        TaskSearchResponse response = new TaskSearchResponse();
        response.setName(task.getName());
        response.setId(task.getId());
        response.setCreatedDateTime(O.getDateInDbFormat(task.getCreatedDateTime().toDate()));
        response.setScheduledOn(O.getDateInDbFormat(task.getScheduledOn().toDate()));
        if (task.getCompletedOn() != null) {
            response.setCompletedOn(O.getDateInDbFormat(task.getCompletedOn().toDate()));
        }
        if (task.getAssignedTo() != null) {
            response.setAssignedTo(task.getAssignedTo().getName());
        }
        response.setTaskStatus(task.getTaskStatus().getName());
        response.setTaskType(task.getTaskType().getName());
        response.setMetadata(metadataMap);
        return response;
    }

    public String getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(String createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public String getCompletedOn() {
        return completedOn;
    }

    public void setCompletedOn(String completedOn) {
        this.completedOn = completedOn;
    }

    public String getScheduledOn() {
        return scheduledOn;
    }

    public void setScheduledOn(String scheduledOn) {
        this.scheduledOn = scheduledOn;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
