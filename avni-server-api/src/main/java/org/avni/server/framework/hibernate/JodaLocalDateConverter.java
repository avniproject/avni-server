package org.avni.server.framework.hibernate;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.sql.Timestamp;

@Converter(autoApply = true)
public class JodaLocalDateConverter implements AttributeConverter<LocalDate, Timestamp> {
    @Override
    public Timestamp convertToDatabaseColumn(LocalDate attribute) {
        if (attribute == null) {
            return null;
        } else {
            return new Timestamp(attribute.toDate().getTime());
        }
    }

    @Override
    public LocalDate convertToEntityAttribute(Timestamp data) {
        if (data == null) {
            return null;
        } else {
            return new LocalDate(data.getTime());
        }
    }
}
