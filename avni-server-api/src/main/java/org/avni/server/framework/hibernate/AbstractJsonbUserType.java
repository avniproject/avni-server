package org.avni.server.framework.hibernate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public abstract class AbstractJsonbUserType<T> extends AbstractUserType<T> implements UserType<T> {
    public final static ObjectMapper mapper = new ObjectMapper().registerModule(new JodaModule());

    @Override
    public int getSqlType() {
        return Types.JAVA_OBJECT;
    }

    @Override
    public T nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        final String cellContent = rs.getString(position);
        if (cellContent == null) {
            return null;
        }
        try {
            return mapper.readValue(cellContent.getBytes("UTF-8"), returnedClass());
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to convert String to Invoice: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void nullSafeSet(final PreparedStatement ps, final Object value, final int idx,
                            final SharedSessionContractImplementor session) throws HibernateException, SQLException {
        if (value == null) {
            ps.setNull(idx, Types.OTHER);
            return;
        }
        try {
            final StringWriter w = new StringWriter();
            mapper.writeValue(w, value);
            w.flush();
            ps.setObject(idx, w.toString(), Types.OTHER);
        } catch (final Exception ex) {
            throw new RuntimeException("Failed to convert Invoice to String: " + ex.getMessage(), ex);
        }
    }
}
