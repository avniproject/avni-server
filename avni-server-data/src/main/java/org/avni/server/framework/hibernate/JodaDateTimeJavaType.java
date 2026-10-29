package org.avni.server.framework.hibernate;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.java.ImmutableMutabilityPlan;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;
import org.joda.time.DateTime;

import java.sql.Timestamp;
import java.util.Date;

public class JodaDateTimeJavaType extends AbstractClassJavaType<DateTime> {
    public static final JodaDateTimeJavaType INSTANCE = new JodaDateTimeJavaType();

    public JodaDateTimeJavaType() {
        super(DateTime.class, ImmutableMutabilityPlan.instance());
    }

    @Override
    public boolean areEqual(DateTime one, DateTime another) {
        if (one == another) return true;
        if (one == null || another == null) return false;
        return one.getMillis() == another.getMillis();
    }

    @Override
    public int extractHashCode(DateTime value) {
        return Long.hashCode(value.getMillis());
    }

    @Override
    public JdbcType getRecommendedJdbcType(JdbcTypeIndicators indicators) {
        return indicators.getJdbcType(SqlTypes.TIMESTAMP);
    }

    @Override
    public String toString(DateTime value) {
        return value.toString();
    }

    @Override
    public DateTime fromString(CharSequence string) {
        return DateTime.parse(string.toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X unwrap(DateTime value, Class<X> type, WrapperOptions options) {
        if (value == null) return null;
        if (DateTime.class.isAssignableFrom(type)) return (X) value;
        if (Timestamp.class.isAssignableFrom(type)) return (X) new Timestamp(value.getMillis());
        if (Date.class.isAssignableFrom(type)) return (X) value.toDate();
        if (Long.class.isAssignableFrom(type)) return (X) Long.valueOf(value.getMillis());
        throw unknownUnwrap(type);
    }

    @Override
    public <X> DateTime wrap(X value, WrapperOptions options) {
        if (value == null) return null;
        if (value instanceof DateTime dateTime) return dateTime;
        if (value instanceof Date date) return new DateTime(date.getTime());
        if (value instanceof Long millis) return new DateTime(millis.longValue());
        throw unknownWrap(value.getClass());
    }
}
