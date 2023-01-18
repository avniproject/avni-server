package org.avni.server.web.request.webapp.task;

import org.avni.server.domain.Concept;
import org.avni.server.domain.task.TaskType;
import org.avni.server.service.ConceptService;
import org.avni.server.web.request.ReferenceDataContract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TaskTypeWebContract extends ReferenceDataContract {
    private String taskTypeName;
    private Map<String, List<String>> metadataSearchFields;

    public static TaskTypeWebContract fromEntity(TaskType taskType, ConceptService conceptService) {
        if (taskType == null) return null;
        TaskTypeWebContract contract = new TaskTypeWebContract();
        contract.taskTypeName = taskType.getType().name();
        contract.setName(taskType.getName());
        contract.setUuid(taskType.getUuid());
        contract.setId(taskType.getId());
        String[] metadataSearchFields = taskType.getMetadataSearchFields();
        if (metadataSearchFields != null) {
            contract.metadataSearchFields = new HashMap<>();
            for (int i = 0; i < metadataSearchFields.length; i++) {
                Concept concept = conceptService.get(metadataSearchFields[i]);
                if (concept.isCoded()) {
                    List<String> answers = concept.getConceptAnswers().stream().map(conceptAnswer ->
                            conceptAnswer.getAnswerConcept().getName()).collect(Collectors.toList());
                    contract.metadataSearchFields.put(concept.getName(), answers);
                } else {
                    contract.metadataSearchFields.put(concept.getName(), null);
                }
            }
        }
        return contract;
    }

    public String getTaskTypeName() {
        return taskTypeName;
    }

    public Map<String, List<String>> getMetadataSearchFields() {
        return metadataSearchFields;
    }
}
