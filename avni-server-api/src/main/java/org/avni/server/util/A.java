package org.avni.server.util;

import java.util.stream.IntStream;

public class A {
    public static int[] findIndicesOf(String[] mainArray, String value) {
        return IntStream.range(0, mainArray.length)
                .filter(i -> value.equals(mainArray[i])).toArray();
    }

    public static void replaceEntriesAtIndicesWith(String[] parameters, int[] indicesOfNonStaticParameters, String value) {
        for (int indexOfNonStaticParameter : indicesOfNonStaticParameters) {
            parameters[indexOfNonStaticParameter] = value;
        }
    }
}
