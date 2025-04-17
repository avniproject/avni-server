package org.avni.server.importer.batch.model;

import org.avni.server.util.S;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;

public class Row extends HashMap<String, String> {
    public static final Pattern TRUE_VALUE = Pattern.compile("y|yes|true|1", Pattern.CASE_INSENSITIVE);
    public static final Pattern FALSE_VALUE = Pattern.compile("n|no|false|0", Pattern.CASE_INSENSITIVE);
    private final String[] headers;
    private final String[] values;

    public Row(String[] headers, String[] values) {
        this.headers = headers;
        this.values = values;
        IntStream.range(0, headers.length).forEach(index -> {
            this.put(headers[index].trim(), values.length > index ? values[index].trim() : "");
        });
    }

    private String nullSafeTrim(String s) {
        if (s == null) {
            return null;
        }
        return s.trim();
    }

    public String[] getHeaders() {
        return Arrays.stream(headers).map(this::nullSafeTrim).toArray(String[]::new);
    }

    public List<String> get(List<String> headers) {
        return headers.stream().map(this::get).collect(Collectors.toList());
    }

    @Override
    public String get(Object key) {
        String k = nullSafeTrim((String) key);
        String s = super.get(k);
        return this.nullSafeTrim(s);
    }

    public String getObservation(Object key) {
        String k = nullSafeTrim((String) key);
        String s = super.getOrDefault(S.doubleQuote(k), super.get(k));
        return this.nullSafeTrim(s);
    }

    @Override
    public String getOrDefault(Object key, String defaultValue) {
        String k = nullSafeTrim((String) key);
        String s = super.getOrDefault(k, defaultValue);
        return this.nullSafeTrim(s);
    }

    @Override
    public String toString() {
        return IntStream.range(0, headers.length)
                .mapToObj(index -> index < values.length ? format("\"%s\"", values[index]) : "\"\"")
                .reduce((c1, c2) -> format("%s,%s", c1, c2))
                .get();
    }

    public Boolean getBool(String header) {
        if (TRUE_VALUE.matcher(String.valueOf(get(header))).matches()) {
            return true;
        } else if (FALSE_VALUE.matcher(String.valueOf(get(header))).matches()) {
            return false;
        }
        return null;
    }
}
