package org.avni.server.domain.factory.task;

import org.avni.server.domain.task.TaskType;
import org.avni.server.domain.task.TaskTypeName;

public class TaskTypeBuilder {
    private final TaskType taskType = new TaskType();

    public TaskType build() {
        return taskType;
    }

    public TaskTypeBuilder withMetadataSearchFields(String ... metadataSearchFields) {
        taskType.setMetadataSearchFields(metadataSearchFields);
        return this;
    }

    public TaskTypeBuilder withTaskTypeName(TaskTypeName taskTypeName) {
        taskType.setType(taskTypeName);
        return this;
    }
}
