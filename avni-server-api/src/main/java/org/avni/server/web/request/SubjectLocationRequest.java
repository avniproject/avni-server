package org.avni.server.web.request;

public class SubjectLocationRequest {
    private PointRequest coordinates;
    private Double accuracy;

    public PointRequest getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(PointRequest coordinates) {
        this.coordinates = coordinates;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }
}