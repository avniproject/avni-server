package org.avni.server.framework.hibernate;

import java.sql.Timestamp;
import org.joda.time.DateTime;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JodaDateTimeConverter implements AttributeConverter<DateTime, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(DateTime attribute) {
        if (attribute == null) {
            return null;
        } else {
            return new Timestamp(attribute.getMillis());
        }
    }

    @Override
    public DateTime convertToEntityAttribute(Timestamp data) {
        if (data == null) {
            return null;
        } else {
            return new DateTime(data.getTime());
        }
    }
}
