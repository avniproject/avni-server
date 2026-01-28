package org.avni.server.importer.batch.csv.creator;

import org.avni.server.geo.Point;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.util.S;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LocationCreator {
    private static final Logger logger = LoggerFactory.getLogger(LocationCreator.class);

    public Point getGeoLocation(Row row, String header, List<String> errorMsgs) {
        String location = row.get(header);
        if (!S.isEmpty(location)) {
            String[] points = location.split(",");
            if (points.length != 2) {
                errorMsgs.add("Invalid 'GPS coordinates'");
                return null;
            }
            try {
                return new Point(Double.parseDouble(points[0].trim()), Double.parseDouble(points[1].trim()));
            } catch (NumberFormatException e) {
                errorMsgs.add("Invalid 'GPS coordinates'");
                return null;
            }
        }
        return null;
    }
}
