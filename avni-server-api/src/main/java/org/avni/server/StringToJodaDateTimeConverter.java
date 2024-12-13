package org.avni.server;

import org.joda.time.DateTime;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToJodaDateTimeConverter implements Converter<String, DateTime> {
    @Override
    public DateTime convert(String source) {
        return new DateTime(source);
    }

    @Override
    public <U> Converter<String, U> andThen(Converter<? super DateTime, ? extends U> after) {
        return Converter.super.andThen(after);
    }
}
