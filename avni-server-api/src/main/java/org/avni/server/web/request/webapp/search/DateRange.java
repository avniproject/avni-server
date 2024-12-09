package org.avni.server.web.request.webapp.search;

public class DateRange extends RangeFilter<String>{
    public DateRange() {
    }
    public DateRange(String minValue, String maxValue) {
        super(minValue, maxValue);
    }
}
