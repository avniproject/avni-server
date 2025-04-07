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

import java.util.List;

public abstract class BulkLocationModifier {
    protected final LocationRepository locationRepository;
    private final ObservationCreator observationCreator;
    private final HeaderCreator headerCreator;

    public BulkLocationModifier(LocationRepository locationRepository, ObservationCreator observationCreator, HeaderCreator headerCreator) {
        this.locationRepository = locationRepository;
        this.observationCreator = observationCreator;
        this.headerCreator = headerCreator;
    }

    protected void updateLocationProperties(Row row, List<String> allErrorMsgs, AddressLevel location) throws ValidationException {
        LocationCreator locationCreator = new LocationCreator();
        Point gpsCoordinates = locationCreator.getGeoLocation(row, LocationHeaderCreator.gpsCoordinates, allErrorMsgs);
        if (gpsCoordinates != null) location.setGpsCoordinates(gpsCoordinates);

        ObservationCollection locationProperties = observationCreator.getObservations(row, headerCreator, allErrorMsgs, FormType.Location, location.getLocationProperties(), null);
        if (!locationProperties.isEmpty()) location.setLocationProperties(locationProperties);
        locationRepository.save(location);
    }
}
