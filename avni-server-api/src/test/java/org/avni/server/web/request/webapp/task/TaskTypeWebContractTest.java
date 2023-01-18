package org.avni.server.web.request.webapp.task;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.metadata.ConceptAnswerBuilder;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.task.TaskTypeBuilder;
import org.avni.server.domain.task.TaskType;
import org.avni.server.domain.task.TaskTypeName;
import org.avni.server.service.ConceptService;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class TaskTypeWebContractTest {
    @Test
    public void fromEntity() {
        String ansConceptUuid = "AnswerConcept-UUID";
        Concept answerConcept = new ConceptBuilder().withUuid(ansConceptUuid).build();
        ConceptAnswer conceptAnswer = new ConceptAnswerBuilder().withAnswerConcept(answerConcept).build();

        String conceptUuid = "ConceptUUID";
        String conceptName = "ConceptName";
        Concept concept = new ConceptBuilder().withName(conceptName).withDataType(ConceptDataType.Coded).withUuid(conceptUuid).withAnswers(conceptAnswer).build();
        ConceptService conceptService = Mockito.mock(ConceptService.class);
        when(conceptService.get(conceptUuid)).thenReturn(concept);
        TaskType taskType = new TaskTypeBuilder().withMetadataSearchFields(conceptUuid).withTaskTypeName(TaskTypeName.Call).build();
        TaskTypeWebContract contract = TaskTypeWebContract.fromEntity(taskType, conceptService);
        assertEquals(1, contract.getMetadataSearchFields().get(conceptName).size());
    }
}
