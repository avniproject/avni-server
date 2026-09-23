package org.avni.server.domain;

import org.avni.server.geo.Point;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class SubjectLocation implements Serializable {
    private Point coordinates;
    private Double accuracy;

    public SubjectLocation() {
    }

    public SubjectLocation(Point coordinates, Double accuracy) {
        this.coordinates = coordinates;
        this.accuracy = accuracy;
    }

    public static SubjectLocation fromMap(Map<String, Object> subjectLocationMap) {
        if (subjectLocationMap == null) {
            return null;
        }

        Map<String, Double> location = (Map<String, Double>) subjectLocationMap.get("coordinates");
        Point coordinates = Point.fromMap(location);
        Double accuracy = (Double) subjectLocationMap.get("accuracy");

        return new SubjectLocation(coordinates, accuracy);
    }

    public Point getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Point coordinates) {
        this.coordinates = coordinates;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    /**
     * The export writes this straight into a cell, so without this it read as
     * org.avni.server.domain.SubjectLocation@403522bf. Matches registration_location, which renders
     * through Point and has always been readable.
     */
    @Override
    public String toString() {
        return coordinates == null ? "" : coordinates.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubjectLocation that = (SubjectLocation) o;
        return Objects.equals(coordinates, that.coordinates) && Objects.equals(accuracy, that.accuracy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinates, accuracy);
    }
}