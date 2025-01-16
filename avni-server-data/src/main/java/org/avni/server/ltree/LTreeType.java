package org.avni.server.ltree;

import org.hibernate.HibernateException;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

//https://stackoverflow.com/a/41381946/5737375

public class LTreeType implements UserType<String> {
    @Override
    public Class returnedClass() {
        return String.class;
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public boolean equals(String o, String o1) {
        if (o == o1) return true;
        if (o == null || o1 == null) return false;

        return o.equals(o1);
    }

    @Override
    public int hashCode(String o) {
        return o.hashCode();
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return rs.getString(position);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index, SharedSessionContractImplementor session) throws SQLException {
        st.setObject(index, value, Types.OTHER);
    }

    @Override
    public String deepCopy(String o) {
        return o;
    }

    @Override
    public Serializable disassemble(String o) {
        return (Serializable) o;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public String replace(String detached, String managed, Object owner) {
        return deepCopy(detached);
    }

    @Override
    public long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
        return UserType.super.getDefaultSqlLength(dialect, jdbcType);
    }

    @Override
    public int getDefaultSqlPrecision(Dialect dialect, JdbcType jdbcType) {
        return UserType.super.getDefaultSqlPrecision(dialect, jdbcType);
    }

    @Override
    public int getDefaultSqlScale(Dialect dialect, JdbcType jdbcType) {
        return UserType.super.getDefaultSqlScale(dialect, jdbcType);
    }

    @Override
    public JdbcType getJdbcType(TypeConfiguration typeConfiguration) {
        return UserType.super.getJdbcType(typeConfiguration);
    }

    @Override
    public BasicValueConverter<String, Object> getValueConverter() {
        return UserType.super.getValueConverter();
    }

    @Override
    public String assemble(Serializable serializable, Object o) throws HibernateException {
        return (String) serializable;
    }
}
