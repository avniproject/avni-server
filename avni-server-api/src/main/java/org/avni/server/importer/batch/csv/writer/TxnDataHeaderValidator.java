package org.avni.server.importer.batch.csv.writer;

import com.google.common.collect.Sets;
import org.avni.server.application.FormMapping;
import org.avni.server.importer.batch.csv.writer.header.HeaderCreator;
import org.avni.server.util.S;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TxnDataHeaderValidator {
    public static void validateHeaders(String[] headers, FormMapping formMapping, HeaderCreator headerCreator) {
        List<String> headerList = new ArrayList<>(Arrays.asList(headers));
        List<String> allErrorMsgs = new ArrayList<>();
        List<String> providedIntendedHeaders = headerList.stream().map(S::unDoubleQuote).toList();
        String[] expectedHeaders = headerCreator.getAllHeaders(formMapping,null);
        String[] expectedIntendedHeaders = Arrays.stream(expectedHeaders).map(S::unDoubleQuote).toArray(String[]::new);
        checkForMissingHeaders(providedIntendedHeaders, allErrorMsgs, Arrays.asList(expectedIntendedHeaders));
        checkForUnknownHeaders(providedIntendedHeaders, allErrorMsgs, Arrays.asList(expectedIntendedHeaders));
        if (!allErrorMsgs.isEmpty()) {
            throw new RuntimeException(createMultiErrorMessage(allErrorMsgs));
        }
    }

    private static String createMultiErrorMessage(List<String> errorMsgs) {
        return String.join(" ", errorMsgs);
    }

    private static void checkForUnknownHeaders(List<String> headerList, List<String> allErrorMsgs, List<String> expectedStandardHeaders) {
        ArrayList<String> workingHeaderList = new ArrayList<>(headerList);
        workingHeaderList.removeIf(StringUtils::isEmpty);
        HashSet<String> expectedHeaders = new HashSet<>(expectedStandardHeaders);
        Sets.SetView<String> unknownHeaders = Sets.difference(new HashSet<>(headerList), expectedHeaders);
        if (!unknownHeaders.isEmpty()) {
            allErrorMsgs.add(String.format(CommonWriterError.ERR_MSG_UNKNOWN_HEADERS, String.join(", ", unknownHeaders)));
        }
    }

    private static void checkForMissingHeaders(List<String> headerList, List<String> allErrorMsgs, List<String> expectedStandardHeaders) {
        HashSet<String> expectedHeaders = new HashSet<>(expectedStandardHeaders);
        HashSet<String> presentHeaders = new HashSet<>(headerList);
        Sets.SetView<String> missingHeaders = Sets.difference(expectedHeaders, presentHeaders);
        if (!missingHeaders.isEmpty()) {
            allErrorMsgs.add(String.format(CommonWriterError.ERR_MSG_MISSING_MANDATORY_FIELDS, String.join(", ", missingHeaders)));
        }
    }
}
