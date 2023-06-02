package org.avni.server.domain;


import java.util.Arrays;
import java.util.List;

public enum ConceptDataType {
    Numeric,
    Text,
    Notes,
    Coded,
    NA,
    Date,
    DateTime,
    Time,
    Duration,
    Image,
    Id,
    Video,
    Subject,
    Location,
    PhoneNumber,
    GroupAffiliation,
    Audio,
    File,
    QuestionGroup,
    Encounter;

    private static final List<ConceptDataType> stringTypes = Arrays.asList(Text, Coded, Notes, Image, Video, Id);
    private static final List<ConceptDataType> dateTypes = Arrays.asList(Date, DateTime, Duration, Time);
    private static final List<ConceptDataType> primitiveTypes = Arrays.asList(Text, DateTime, Date, Time, Numeric, Notes);
    public static final List<ConceptDataType> dashboardFilterSupportedTypes = Arrays.asList(Numeric, Text, Notes, Coded, Date, DateTime, Time, Id, QuestionGroup, Encounter, Location);
    public static final List<ConceptDataType> mediaDataTypes = Arrays.asList(Image, Video, File, Audio);

    public static boolean stringType(String string) {
        return stringTypes.contains(ConceptDataType.valueOf(string));
    }

    public static boolean dateType(String dataType) {
        return dateTypes.contains(ConceptDataType.valueOf(dataType));
    }

    public static boolean isPrimitiveType(String dataType) {
        return primitiveTypes.contains(ConceptDataType.valueOf(dataType));
    }

    public static boolean matches(ConceptDataType conceptDataType, String dataType) {
        return conceptDataType.toString().equals(dataType);
    }

    public static boolean matches(String dataType, ConceptDataType ... conceptDataTypes) {
        ConceptDataType found = Arrays.stream(conceptDataTypes).filter(conceptDataType -> conceptDataType.toString().equals(dataType)).findAny().orElse(null);
        return found != null;
    }

    public static boolean isGroupQuestion(String dataType) {
        return ConceptDataType.valueOf(dataType).equals(QuestionGroup);
    }

    public static boolean isMedia(String dataType) {
        return mediaDataTypes.contains(ConceptDataType.valueOf(dataType));
    }
}
