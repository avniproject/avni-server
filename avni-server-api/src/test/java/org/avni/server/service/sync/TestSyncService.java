package org.avni.server.service.sync;

import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.web.IndividualController;
import org.avni.server.web.ProgramEnrolmentController;
import org.avni.server.web.SyncController;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TestSyncService {
    private final SyncController syncController;
    private final IndividualController individualController;
    private final ProgramEnrolmentController programEnrolmentController;

    @Autowired
    public TestSyncService(SyncController syncController, IndividualController individualController, ProgramEnrolmentController programEnrolmentController) {
        this.syncController = syncController;
        this.individualController = individualController;
        this.programEnrolmentController = programEnrolmentController;
    }

    public List getSyncDetails() {
        List<EntitySyncStatusContract> contracts = SyncEntityName.getNonTransactionalEntities().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false, true);
        return ((JsonObject) response.getBody()).getList("syncDetails");
    }

    public List<Individual> getSubjects(SubjectType subjectType) {
        return this.getSubjects(subjectType, DateTime.now().minusDays(1));
    }

    public List<Individual> getSubjects(SubjectType subjectType, DateTime lastModifiedDateTime) {
        return getSubjects(subjectType, lastModifiedDateTime, DateTime.now());
    }

    public List<Individual> getSubjects(SubjectType subjectType, DateTime lastModifiedDateTime, DateTime now) {
        CollectionModel<EntityModel<Individual>> individuals = individualController.getIndividualsByOperatingIndividualScope(lastModifiedDateTime, now, subjectType.getUuid(), PageRequest.of(0, 10));
        return individuals.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }

    public List<ProgramEnrolment> getEnrolments(Program program) throws Exception {
        return this.getEnrolments(program, DateTime.now().minusDays(1));
    }

    public List<ProgramEnrolment> getEnrolments(Program program, DateTime lastModifiedDateTime) throws Exception {
        return getEnrolments(program, lastModifiedDateTime, DateTime.now());
    }

    public List<ProgramEnrolment> getEnrolments(Program program, DateTime lastModifiedDateTime, DateTime now) throws Exception {
        CollectionModel<EntityModel<ProgramEnrolment>> enrolments = programEnrolmentController.getProgramEnrolmentsByOperatingIndividualScope(lastModifiedDateTime, now, program.getUuid(), PageRequest.of(0, 10));
        return enrolments.getContent().stream().map(Resource::getContent).collect(Collectors.toList());
    }
}
