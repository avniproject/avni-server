package org.avni.server.domain;

import org.avni.server.geo.Point;
import java.io.Serializable;
import java.util.Map;

public class SubjectLocation implements Serializable {
    private Point location;
    private Double accuracy;

    public SubjectLocation() {
    }

    public SubjectLocation(Point location, Double accuracy) {
        this.location = location;
        this.accuracy = accuracy;
    }

    public static SubjectLocation fromMap(Map<String, Object> subjectLocationMap) {
        if (subjectLocationMap == null) {
            return null;
        }
        
        Map<String, Double> coordinates = (Map<String, Double>) subjectLocationMap.get("coordinates");
        Point location = Point.fromMap(coordinates);
        Double accuracy = (Double) subjectLocationMap.get("accuracy");
        
        return new SubjectLocation(location, accuracy);
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }
}