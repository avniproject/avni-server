package org.avni.server.web.request.webapp.search;

public class IntegerRange extends RangeFilter<Integer>{
    public IntegerRange() {
    }

    public IntegerRange(Integer minValue, Integer maxValue) {
        super(minValue, maxValue);
    }
}
