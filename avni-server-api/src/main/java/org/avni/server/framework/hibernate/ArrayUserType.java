package org.avni.server.framework.hibernate;

import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.*;

public class ArrayUserType extends AbstractUserType<String[]> {
    @Override
    public int getSqlType() {
        return Types.ARRAY;
    }

    @Override
    public Class returnedClass() {
        return String[].class;
    }

    @Override
    public String[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Array array = rs.getArray(position);
        return array != null ? (String[]) array.getArray() : null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String[] value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value != null && st != null) {
            Connection connection = session.getJdbcConnectionAccess().obtainConnection();
            Array array = connection.createArrayOf("text", value);
            st.setArray(index, array);
        } else {
            st.setNull(index, Types.ARRAY);
        }
    }

    @Override
    public boolean equals(String[] x, String[] y) {
        if (x == null && y == null) {
            return true;
        }
        if (x == null || y == null) {
            return false;
        }

        if (x == y) {
            return true;
        }

        if (!(x instanceof String[] || y instanceof String[])) {
            return false;
        }

        String[] xArray = x;
        String[] yArray = y;
        if (xArray.length != yArray.length) {
            return false;
        }
        for (int i = 0; i < xArray.length; i++) {
            if (!xArray[i].equals(yArray[i])) {
                return false;
            }
        }
        return true;
    }
}
