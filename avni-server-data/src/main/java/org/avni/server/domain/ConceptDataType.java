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
    ImageV2,
    Id,
    Video,
    Subject,
    Location,
    PhoneNumber,
    GroupAffiliation,
    Audio,
    File,
    QuestionGroup,
    Encounter,
    Signature;

    private static final List<ConceptDataType> dateTypes = Arrays.asList(Date, DateTime, Duration, Time);
    public static final List<ConceptDataType> dashboardFilterSupportedTypes = Arrays.asList(Numeric, Text, Notes, Coded, Date, DateTime, Time, Id, Location);
    public static final List<ConceptDataType> mediaDataTypes = Arrays.asList(Image, ImageV2, Video, File, Audio);
    public static final List<ConceptDataType> multiSelectTypes = Arrays.asList(Coded, Subject, Image, ImageV2, Video, File, Encounter);

    public static boolean dateType(String dataType) {
        return dateTypes.contains(ConceptDataType.valueOf(dataType));
    }

    public static boolean matches(ConceptDataType conceptDataType, String dataType) {
        return conceptDataType.toString().equals(dataType);
    }

    public static boolean matches(String dataType, ConceptDataType ... conceptDataTypes) {
        ConceptDataType found = Arrays.stream(conceptDataTypes).filter(conceptDataType -> conceptDataType.toString().equals(dataType)).findAny().orElse(null);
        return found != null;
    }

    public static boolean isQuestionGroup(String dataType) {
        return ConceptDataType.valueOf(dataType).equals(QuestionGroup);
    }

    public static boolean isMedia(String dataType) {
        return mediaDataTypes.contains(ConceptDataType.valueOf(dataType));
    }
}
