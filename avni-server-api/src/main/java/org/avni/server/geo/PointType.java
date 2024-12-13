package org.avni.server.geo;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.geometric.PGpoint;

/**
 * A Hibernate <b>UserType</b> for PostgreSQL's <b>point</b> type.
 *
 * @author Jesse Costello-Good
 * @version $Id$
 */
public class PointType implements UserType<Point> {
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class returnedClass() {
        return Point.class;
    }

    @Override
    public boolean equals(Point o, Point o1) {
        if (o == null && o1 == null)
            return true;
        else if (o == null || o1 == null)
            return false;
        return o.equals(o1);
    }

    @Override
    public Point nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        PGpoint value = (PGpoint) rs.getObject(position);

        if (value == null) {
            return null;
        } else {
            return new Point(value.x, value.y);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Point value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, java.sql.Types.OTHER);
        } else {
            st.setObject(index, new PGpoint(value.getX(), value.getY()));
        }
    }

    @Override
    public Point deepCopy(Point o) throws HibernateException {
        if (o == null)
            return null;

        try {
            return (Point) o.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public int hashCode(Point o) throws HibernateException {
        return o.hashCode();
    }

    @Override
    public Serializable disassemble(Point o) throws HibernateException {
        return (Serializable) o;
    }

    @Override
    public Point assemble(Serializable cached, Object owner) throws HibernateException {
        return (Point) cached;
    }

    @Override
    public Point replace(Point original, Point managed, Object owner) {
        return original;
    }
}
