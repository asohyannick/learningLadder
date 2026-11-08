package com.learningLadder.learningLadder.service.timeTableService;

import com.learningLadder.learningLadder.dto.timeTableDto.TimeTableResponseDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


@Component
public class TimeTablePdfGenerator {

    private static final PDRectangle PAGE =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 36f;
    private static final float TEXT_SIZE = 8f;
    private static final float LEADING = 10f;
    private static final float PAD = 4f;
    private static final int MAX_CELL_LINES = 4;

    private static final String[] HEADERS =
            {"Course", "Title", "Date", "Time", "Format", "Location / link", "Status"};
    private static final float[] WIDTHS = {130f, 150f, 150f, 80f, 60f, 130f, 69f};
    private static final float TABLE_WIDTH = 769f;

    private static final int[] BRAND = {79, 70, 229};
    private static final int[] STRIPE = {249, 250, 251};
    private static final int[] WHITE = {255, 255, 255};
    private static final int[] INK = {31, 41, 55};
    private static final int[] MUTED = {107, 114, 128};

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    public byte[] generate(List<TimeTableResponseDto> entries, List<String> filters) {
        try (Builder pdf = new Builder()) {
            String count = entries.size() + (entries.size() == 1 ? " entry" : " entries");
            pdf.title("Timetable", "Generated " + STAMP.format(Instant.now()) + " - " + count, filters);
            pdf.drawRow(HEADERS, true, false);
            for (int i = 0; i < entries.size(); i++) {
                pdf.drawRow(toCells(entries.get(i)), false, i % 2 == 1);
            }
            return pdf.finish();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not generate the timetable PDF", ex);
        }
    }

    private static String[] toCells(TimeTableResponseDto t) {
        String date = t.recurring() && t.dayOfWeek() != null
                ? "Every " + t.dayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                  + "\nfrom " + DATE.format(t.startDate())
                  + (t.endDate() != null ? "\nto " + DATE.format(t.endDate()) : "")
                : DATE.format(t.startDate());

        String time = CLOCK.format(t.startTime()) + " - " + CLOCK.format(t.endTime())
                + "\n(" + t.timezone() + ")";

        return new String[]{
                t.courseName(),
                t.title(),
                date,
                time,
                label(t.sessionMode()),
                join(t.location(), t.meetingUrl()),
                label(t.status())
        };
    }

    private static String join(String first, String second) {
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first + "\n" + second;
        }
        return hasFirst ? first : (hasSecond ? second : "");
    }

    private static String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }


    private static float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    private static String sanitize(String text, PDFont font) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                out.append(' ');
                continue;
            }
            String ch = new String(Character.toChars(codePoint));
            try {
                font.encode(ch);
                out.append(ch);
            } catch (Exception ex) {
                out.append('?');
            }
        }
        return out.toString().replaceAll(" +", " ").trim();
    }

    private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();

        for (String paragraph : (text == null ? "" : text).split("\n", -1)) {
            String clean = sanitize(paragraph, font);
            if (clean.isEmpty()) {
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : clean.split(" ")) {
                String rest = word;
                while (width(rest, font, size) > maxWidth) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    int cut = fitting(rest, font, size, maxWidth);
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                if (rest.isEmpty()) {
                    continue;
                }
                String candidate = line.isEmpty() ? rest : line + " " + rest;
                if (width(candidate, font, size) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(rest);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }

        if (lines.isEmpty()) {
            lines.add("-");
        }
        if (lines.size() > MAX_CELL_LINES) {
            List<String> shortened = new ArrayList<>(lines.subList(0, MAX_CELL_LINES));
            String last = shortened.get(MAX_CELL_LINES - 1);
            while (!last.isEmpty() && width(last + "...", font, size) > maxWidth) {
                last = last.substring(0, last.length() - 1);
            }
            shortened.set(MAX_CELL_LINES - 1, last + "...");
            return shortened;
        }
        return lines;
    }

    private static int fitting(String word, PDFont font, float size, float maxWidth) throws IOException {
        int cut = 1;
        while (cut < word.length() && width(word.substring(0, cut + 1), font, size) <= maxWidth) {
            cut++;
        }
        return cut;
    }

    private static final class Builder implements AutoCloseable {

        private final PDDocument doc = new PDDocument();
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final List<PDPage> pages = new ArrayList<>();
        private PDPageContentStream stream;
        private float y;

        void title(String heading, String subtitle, List<String> filters) throws IOException {
            newPage();
            text(heading, bold, 16f, MARGIN, y - 16f, BRAND);
            y -= 24f;
            text(sanitize(subtitle, regular), regular, 9f, MARGIN, y - 9f, MUTED);
            y -= 14f;
            if (filters != null && !filters.isEmpty()) {
                for (String line : wrap("Filters: " + String.join(", ", filters), regular, 8f, TABLE_WIDTH)) {
                    text(line, regular, 8f, MARGIN, y - 8f, MUTED);
                    y -= 11f;
                }
            }
            y -= 8f;
        }

        void drawRow(String[] cells, boolean header, boolean shaded) throws IOException {
            PDFont font = header ? bold : regular;

            List<List<String>> columns = new ArrayList<>();
            int lineCount = 1;
            for (int i = 0; i < cells.length; i++) {
                List<String> wrapped = wrap(cells[i], font, TEXT_SIZE, WIDTHS[i] - 2 * PAD);
                columns.add(wrapped);
                lineCount = Math.max(lineCount, wrapped.size());
            }
            float height = lineCount * LEADING + 2 * PAD;

            if (y - height < MARGIN) {
                newPage();
                if (!header) {
                    drawRow(HEADERS, true, false);
                }
            }

            if (header || shaded) {
                int[] fill = header ? BRAND : STRIPE;
                stream.setNonStrokingColor(fill[0], fill[1], fill[2]);
                stream.addRect(MARGIN, y - height, TABLE_WIDTH, height);
                stream.fill();
            }

            float x = MARGIN;
            for (int i = 0; i < columns.size(); i++) {
                float baseline = y - PAD - TEXT_SIZE + 1f;
                for (String line : columns.get(i)) {
                    text(line, font, TEXT_SIZE, x + PAD, baseline, header ? WHITE : INK);
                    baseline -= LEADING;
                }
                x += WIDTHS[i];
            }

            stream.setStrokingColor(229, 231, 235);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y - height);
            stream.lineTo(MARGIN + TABLE_WIDTH, y - height);
            stream.stroke();

            y -= height;
        }

        byte[] finish() throws IOException {
            closeStream();
            int total = pages.size();
            for (int i = 0; i < total; i++) {
                String label = "Page " + (i + 1) + " of " + total;
                try (PDPageContentStream footer = new PDPageContentStream(
                        doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.setNonStrokingColor(MUTED[0], MUTED[1], MUTED[2]);

                    footer.beginText();
                    footer.setFont(regular, 8f);
                    footer.newLineAtOffset(MARGIN, 22f);
                    footer.showText("Learning Ladder - Timetable");
                    footer.endText();

                    float labelWidth = width(label, regular, 8f);
                    footer.beginText();
                    footer.setFont(regular, 8f);
                    footer.newLineAtOffset(PAGE.getWidth() - MARGIN - labelWidth, 22f);
                    footer.showText(label);
                    footer.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }

        @Override
        public void close() throws IOException {
            closeStream();
            doc.close();
        }

        private void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PAGE);
            doc.addPage(page);
            pages.add(page);
            stream = new PDPageContentStream(doc, page);
            y = PAGE.getHeight() - MARGIN;
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void text(String value, PDFont font, float size, float x, float baseline, int[] rgb)
                throws IOException {
            stream.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(value);
            stream.endText();
        }
    }
}