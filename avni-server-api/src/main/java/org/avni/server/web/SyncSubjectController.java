package org.avni.server.web;

import org.avni.server.service.SubjectSyncResponseBuilderService;
import org.avni.server.web.resourceProcessors.*;
import org.avni.server.web.response.SyncSubjectResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
public class SyncSubjectController{

    private SubjectSyncResponseBuilderService subjectSyncResponseBuilderService;

    @Autowired
    public SyncSubjectController(SubjectSyncResponseBuilderService subjectSyncResponseBuilderService) {
        this.subjectSyncResponseBuilderService = subjectSyncResponseBuilderService;
    }

    public EntityModel<HashMap<String, Object>> process(SyncSubjectResponse syncSubjectResponse) {
        HashMap<String, Object> result = new HashMap<>();
        result.put("individual", new IndividualResourceProcessor().process(new EntityModel<>(syncSubjectResponse.getIndividual())));
        result.put("programEnrolments", syncSubjectResponse.getProgramEnrolments().stream().map(programEnrolment -> new ProgramEnrolmentResourceProcessor().process(new EntityModel<>(programEnrolment))));
        result.put("programEncounters", syncSubjectResponse.getProgramEncounters().stream().map(programEncounter1 -> new ProgramEncounterResourceProcessor().process(new EntityModel<>(programEncounter1))));
        result.put("encounters", syncSubjectResponse.getEncounters().stream().map(encounter -> new EncounterResourceProcessor().process(new EntityModel<>(encounter))));
        result.put("checklists", syncSubjectResponse.getChecklists().stream().map(checklist -> new ChecklistResourceProcessor().process(new EntityModel<>(checklist))));
        result.put("checklistItems", syncSubjectResponse.getChecklistItems().stream().map(checklistItem -> new ChecklistItemResourceProcessor().process(new EntityModel<>(checklistItem))));
        result.put("groupSubjects", syncSubjectResponse.getGroupSubjects().stream().map(groupSubject -> new GroupSubjectResourceProcessor().process(new EntityModel<>(groupSubject))));
        result.put("individualRelationships", syncSubjectResponse.getIndividualRelationships().stream().map(individualRelationship -> new IndividualRelationshipResourceProcessor().process(new EntityModel<>(individualRelationship))));
        return new EntityModel<>(result);
    }

    @GetMapping("/subject/{uuid}/allEntities")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<EntityModel<HashMap<String, Object>>> getSubjectDetailsForSync(@PathVariable String uuid) {
        return ResponseEntity.ok(process(subjectSyncResponseBuilderService.getSubject(uuid))) ;
    }
}
