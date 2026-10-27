package org.avni.server.service.abdm.hip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// DEMO-ONLY abdm-hackathon: never merged.
// The tolerant name match NHA asks of patient-led linking once the mobile number has matched (USER_INIT_LINK_603): an
// ABHA name from Aadhaar often carries an initial or a word Avni's record does not. Names are compared as words of
// letters in any script, in any order and case; every word of the shorter name must match a different word of the
// longer one, whole words first, then an initial matching the word it starts; at least one full word must match. A
// one-word name matches only a one-word name: on a family phone, "Lakshmi" must not match a relative's "Lakshmi Priya".
public final class AbdmNames {
    private AbdmNames() {
    }

    public static boolean sameName(String held, String given) {
        List<String> a = words(held);
        List<String> b = words(given);
        if (a.isEmpty() || b.isEmpty()) return false;
        if ((a.size() == 1) != (b.size() == 1)) return false;
        List<String> shorter = new ArrayList<>(a.size() <= b.size() ? a : b);
        List<String> longer = new ArrayList<>(a.size() <= b.size() ? b : a);
        boolean fullWord = false;
        // Whole words first, so an initial never takes a word a whole-word match needs ("K Kavitha", "Kavitha Kannan").
        for (String word : new ArrayList<>(shorter)) {
            if (longer.remove(word)) {
                shorter.remove(word);
                fullWord |= word.codePointCount(0, word.length()) > 1;
            }
        }
        for (String word : shorter) {
            String match = longer.stream().filter(w -> initialOf(word, w) || initialOf(w, word)).findFirst().orElse(null);
            if (match == null) return false;
            longer.remove(match);
        }
        return fullWord;
    }

    private static boolean initialOf(String initial, String word) {
        return initial.codePointCount(0, initial.length()) == 1 && word.codePointCount(0, word.length()) > 1 && word.startsWith(initial);
    }

    // Runs of letters (and the marks that combine with them, as in Indic scripts), lowercased.
    private static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        if (name == null) return out;
        StringBuilder word = new StringBuilder();
        name.toLowerCase(Locale.ROOT).codePoints().forEach(c -> {
            if (Character.isLetter(c) || Character.getType(c) == Character.NON_SPACING_MARK || Character.getType(c) == Character.COMBINING_SPACING_MARK) {
                word.appendCodePoint(c);
            } else if (word.length() > 0) {
                out.add(word.toString());
                word.setLength(0);
            }
        });
        if (word.length() > 0) out.add(word.toString());
        return out;
    }
}
