package org.avni.server.service.abdm.fhir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// DEMO-ONLY abdm-hackathon: never merged.
// A plain-text PDF (A4, Helvetica, uncompressed), for the PDF copy NHA's simple bundle form allows. No library: the
// server has none, and a page of lines needs only the PDF 1.4 basics. Helvetica covers Latin-1 only: other characters
// (Kannada, Devanagari) print as '?', so the structured record stays the one to read.
final class SimplePdf {
    private static final int WIDTH = 595, HEIGHT = 842, MARGIN = 50, SIZE = 10, LEADING = 14, TITLE_SIZE = 14;
    private static final int LINES_PER_PAGE = (HEIGHT - 2 * MARGIN - 2 * LEADING) / LEADING;
    private static final int WRAP = 95;

    private SimplePdf() {
    }

    static byte[] of(String title, List<String> lines) {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) wrapped.addAll(wrap(line == null ? "" : line));
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < Math.max(1, wrapped.size()); i += LINES_PER_PAGE) {
            pages.add(wrapped.subList(Math.min(i, wrapped.size()), Math.min(i + LINES_PER_PAGE, wrapped.size())));
        }

        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pages.size(); p++) kids.append(4 + 2 * p).append(" 0 R ");
        objects.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pages.size() + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        for (int p = 0; p < pages.size(); p++) {
            int contentId = 5 + 2 * p;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + WIDTH + " " + HEIGHT + "] /Resources << /Font << /F1 3 0 R >> >> /Contents "
                    + contentId + " 0 R >>");
            String stream = content(p == 0 ? title : null, pages.get(p));
            objects.add("<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "\nendstream");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) table.append(String.format("%010d 00000 n \n", offset));
        write(out, table + "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static String content(String title, List<String> lines) {
        StringBuilder s = new StringBuilder("BT\n");
        int y = HEIGHT - MARGIN;
        if (title != null) {
            s.append("/F1 ").append(TITLE_SIZE).append(" Tf\n1 0 0 1 ").append(MARGIN).append(' ').append(y).append(" Tm\n(")
                    .append(escape(title)).append(") Tj\n");
            y -= 2 * LEADING;
        }
        s.append("/F1 ").append(SIZE).append(" Tf\n");
        for (String line : lines) {
            s.append("1 0 0 1 ").append(MARGIN).append(' ').append(y).append(" Tm\n(").append(escape(line)).append(") Tj\n");
            y -= LEADING;
        }
        return s.append("ET").toString();
    }

    private static List<String> wrap(String line) {
        List<String> out = new ArrayList<>();
        String rest = line;
        while (rest.length() > WRAP) {
            int cut = rest.lastIndexOf(' ', WRAP);
            if (cut <= 0) cut = WRAP;
            out.add(rest.substring(0, cut));
            rest = rest.substring(cut).stripLeading();
        }
        out.add(rest);
        return out;
    }

    private static String escape(String text) {
        StringBuilder s = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\u2013' || c == '\u2014') s.append('-');
            else if (c == '\\' || c == '(' || c == ')') s.append('\\').append(c);
            else if (c >= 32 && c <= 255 && c != 127) s.append(c);
            else s.append('?');
        }
        return s.toString();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }
}
