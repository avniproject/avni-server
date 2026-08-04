package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormType;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.ValidationException;
import org.avni.server.geo.Point;
import org.avni.server.importer.batch.csv.creator.LocationCreator;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.importer.batch.csv.writer.header.LocationHeaderCreator;
import org.avni.server.importer.batch.model.Row;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.avni.server.domain.Concept;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;

public abstract class BulkLocationModifier {
    protected final LocationRepository locationRepository;
    protected final ObservationCreator observationCreator;
    protected final HeaderCreator headerCreator;
    private Long conceptsMemoJobExecutionId;
    private String[] conceptsMemoHeaders;
    private Set<Concept> conceptsMemo;

    public BulkLocationModifier(LocationRepository locationRepository, ObservationCreator observationCreator, HeaderCreator headerCreator) {
        this.locationRepository = locationRepository;
        this.observationCreator = observationCreator;
        this.headerCreator = headerCreator;
    }

    protected void updateLocationProperties(Row row, List<String> allErrorMsgs, AddressLevel location) throws ValidationException {
        LocationCreator locationCreator = new LocationCreator();
        Point gpsCoordinates = locationCreator.getGeoLocation(row, LocationHeaderCreator.gpsCoordinates, allErrorMsgs);
        if (gpsCoordinates != null) location.setGpsCoordinates(gpsCoordinates);

        ObservationCollection locationProperties = observationCreator.getObservations(row, headerCreator, allErrorMsgs, FormType.Location, location.getLocationProperties(), null, conceptsInHeader(row.getHeaders()));
        if (!locationProperties.isEmpty()) location.setLocationProperties(locationProperties);
        locationRepository.save(location);
    }

    // The import step runs chunk(1), so anything resolved in a writer's write() happens once per ROW.
    // The header->concept mapping is file-invariant: memoise it per job execution (the worker is
    // single-threaded). Outside a step (integration tests) resolve per call (avni-product#1897).
    private Set<Concept> conceptsInHeader(String[] fileHeaders) {
        StepContext stepContext = StepSynchronizationManager.getContext();
        if (stepContext == null) {
            return observationCreator.getConceptsInHeader(headerCreator, null, fileHeaders);
        }
        Long jobExecutionId = stepContext.getStepExecution().getJobExecutionId();
        if (!jobExecutionId.equals(conceptsMemoJobExecutionId) || !Arrays.equals(fileHeaders, conceptsMemoHeaders)) {
            conceptsMemo = observationCreator.getConceptsInHeader(headerCreator, null, fileHeaders);
            conceptsMemoJobExecutionId = jobExecutionId;
            conceptsMemoHeaders = fileHeaders;
        }
        return conceptsMemo;
    }
}
