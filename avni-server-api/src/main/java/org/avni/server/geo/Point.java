package org.avni.server.geo;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.Map;

/**
 * A simple immutable point object.
 *
 * @author Jesse Costello-Good
 * @version $Id$
 */
public class Point implements Serializable, Cloneable {

    private final double x;
    private final double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Point fromMap(Map<String, Double> location) {
        return new Point(location.get("X"), location.get("Y"));
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Point2D asPoint2D() {
        return new Point2D.Double(x, y);
    }

    public boolean equals(Object obj) {
        if (obj instanceof Point) {
            return x == ((Point) obj).x && y == ((Point) obj).y;
        }
        return super.equals(obj);
    }

    public int hashCode() {
        return (int) x * 31 + (int) y;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public String toString() {
        return String.format("\"%f,%f\"", x, y);
    }
}
