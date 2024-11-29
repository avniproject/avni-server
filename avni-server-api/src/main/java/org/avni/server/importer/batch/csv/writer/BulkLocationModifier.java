package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormType;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.geo.Point;
import org.avni.server.importer.batch.csv.creator.LocationCreator;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.csv.writer.header.LocationHeaders;
import org.avni.server.importer.batch.model.Row;

import java.util.List;

public abstract class BulkLocationModifier {
    protected final LocationRepository locationRepository;
    private final ObservationCreator observationCreator;
    protected static final LocationHeaders headers = new LocationHeaders();

    public BulkLocationModifier(LocationRepository locationRepository, ObservationCreator observationCreator) {
        this.locationRepository = locationRepository;
        this.observationCreator = observationCreator;
    }

    protected void updateLocationProperties(Row row, List<String> allErrorMsgs, AddressLevel location) {
        LocationCreator locationCreator = new LocationCreator();
        Point gpsCoordinates = locationCreator.getGeoLocation(row, LocationHeaders.gpsCoordinates, allErrorMsgs);
        if (gpsCoordinates != null) location.setGpsCoordinates(gpsCoordinates);
        ObservationCollection locationProperties = observationCreator.getObservations(row, headers, allErrorMsgs, FormType.Location, location.getLocationProperties());
        if (!locationProperties.isEmpty()) location.setLocationProperties(locationProperties);
        locationRepository.save(location);
    }
}
