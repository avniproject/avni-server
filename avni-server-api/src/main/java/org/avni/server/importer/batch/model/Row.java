package org.avni.server.importer.batch.model;

import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.S;
import org.joda.time.LocalDate;
import org.springframework.util.StringUtils;

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
    private final String[] originalHeaders;
    private final String[] originalValues;

    public Row(String[] headers, String[] values) {
        // Drop blank header columns (spreadsheet export padding) - import loops over them turn quadratic
        // (avni-product#1897). Raw arrays are kept so toString() stays aligned with the error file's raw header line.
        this.originalHeaders = headers;
        this.originalValues = values;
        int[] namedColumns = IntStream.range(0, headers.length)
                .filter(index -> headers[index] != null && !headers[index].trim().isEmpty())
                .toArray();
        this.headers = IntStream.of(namedColumns).mapToObj(index -> headers[index]).toArray(String[]::new);
        this.values = IntStream.of(namedColumns)
                .mapToObj(index -> values.length > index && values[index] != null ? values[index] : "")
                .toArray(String[]::new);
        IntStream.range(0, this.headers.length).forEach(index ->
                this.put(this.headers[index].trim(), this.values[index].trim()));
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
        String s = super.getOrDefault(S.doubleQuote(k), super.get(k));
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
        return IntStream.range(0, originalHeaders.length)
                .mapToObj(index -> index < originalValues.length ? format("\"%s\"", originalValues[index]) : "\"\"")
                .reduce((c1, c2) -> format("%s,%s", c1, c2))
                .orElse("");
    }

    public Boolean getBool(String header) {
        if (TRUE_VALUE.matcher(String.valueOf(get(header))).matches()) {
            return true;
        } else if (FALSE_VALUE.matcher(String.valueOf(get(header))).matches()) {
            return false;
        }
        return null;
    }

    public LocalDate ensureDateIsPresentAndNotInFuture(String headerColumnName, List<String> errorMsgs) {
        try {
            String rowValue = this.get(headerColumnName);
            if (!StringUtils.hasText(rowValue)) {
                errorMsgs.add(String.format("Value required for mandatory field: '%s'", headerColumnName));
                return null;
            }
            LocalDate date = DateTimeUtil.parseFlexibleDate(rowValue);
            if (date.isAfter(LocalDate.now())) {
                errorMsgs.add(String.format("'%s' %s cannot be in future", headerColumnName, rowValue));
                return null;
            }
            return date;
        } catch (IllegalArgumentException ex) {
            errorMsgs.add(String.format("Invalid '%s'", headerColumnName));
        }
        return null;
    }
}
