package com.learningLadder.learningLadder.service.reviewService;

import com.learningLadder.learningLadder.dto.reviewDto.ReviewResponseDto;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReviewPdfGenerator {

    private static final PDRectangle PAGE_SIZE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 36f;
    private static final float FOOTER_RESERVE = 24f;
    private static final int MAX_COMMENT_CHARS = 1_200;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    public byte[] generate(List<ReviewResponseDto> reviews, List<String> filters) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Layout layout = new Layout(document);

            layout.text("Course Reviews Report", layout.bold, 18, Color.BLACK, 0);
            layout.text("Generated " + DATE_FORMAT.format(Instant.now()) + " UTC  |  "
                    + reviews.size() + " review(s)", layout.regular, 9, Color.DARK_GRAY, 0);
            layout.text(filters.isEmpty() ? "Filters: none" : "Filters: " + String.join(", ", filters),
                    layout.regular, 9, Color.DARK_GRAY, 0);
            layout.gap(6);
            layout.separator();

            for (ReviewResponseDto review : reviews) {
                writeReview(layout, review);
            }

            layout.finish();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate reviews PDF", ex);
        }
    }

    private void writeReview(Layout layout, ReviewResponseDto r) throws IOException {
        layout.ensureSpace(60);

        String title = r.title() == null || r.title().isBlank() ? "(no title)" : r.title();
        layout.text(r.rating() + "/5  -  " + title, layout.bold, 11, Color.BLACK, 0);

        String student = join(r.studentFirstName(), r.studentLastName());
        String meta = "Course: " + orDash(r.courseName())
                + "   |   Student: " + orDash(student)
                + "   |   Status: " + (r.status() == null ? "-" : r.status().name())
                + (r.verifiedEnrollment() ? "   |   Verified enrollment" : "")
                + "   |   Helpful: " + r.helpfulCount()
                + "   |   " + (r.createdAt() == null ? "-" : DATE_FORMAT.format(r.createdAt()));
        layout.text(meta, layout.regular, 8, Color.GRAY, 0);

        if (r.comment() != null && !r.comment().isBlank()) {
            layout.paragraph(truncate(r.comment(), MAX_COMMENT_CHARS), layout.regular, 9, Color.BLACK, 8);
        }
        if (r.tutorReply() != null && !r.tutorReply().isBlank()) {
            layout.paragraph("Tutor reply: " + truncate(r.tutorReply(), MAX_COMMENT_CHARS),
                    layout.regular, 9, new Color(0x1F, 0x4E, 0x79), 8);
        }

        layout.gap(4);
        layout.separator();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String join(String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) present.add(part);
        }
        return String.join(" ", present);
    }

    private static final class Layout {

        private final PDDocument document;
        final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final float contentWidth = PAGE_SIZE.getWidth() - 2 * MARGIN;

        private PDPageContentStream stream;
        private float y;

        Layout(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN;
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN + FOOTER_RESERVE) newPage();
        }

        void gap(float amount) {
            y -= amount;
        }

        void text(String value, PDFont font, float size, Color color, float indent) throws IOException {
            paragraph(value, font, size, color, indent);
        }

        void paragraph(String value, PDFont font, float size, Color color, float indent) throws IOException {
            for (String line : wrap(value, font, size, contentWidth - indent)) {
                ensureSpace(size + 4);
                stream.beginText();
                stream.setFont(font, size);
                stream.setNonStrokingColor(color);
                stream.newLineAtOffset(MARGIN + indent, y - size);
                stream.showText(line);
                stream.endText();
                y -= size + 3;
            }
        }

        void separator() throws IOException {
            ensureSpace(10);
            stream.setStrokingColor(Color.LIGHT_GRAY);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(PAGE_SIZE.getWidth() - MARGIN, y);
            stream.stroke();
            y -= 8;
        }

        void finish() throws IOException {
            stream.close();
            stream = null;

            int total = document.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = document.getPage(i);
                try (PDPageContentStream footer = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String label = "Page " + (i + 1) + " of " + total;
                    float width = regular.getStringWidth(label) / 1000f * 9f;
                    footer.beginText();
                    footer.setFont(regular, 9);
                    footer.setNonStrokingColor(Color.GRAY);
                    footer.newLineAtOffset((PAGE_SIZE.getWidth() - width) / 2f, MARGIN - 12f);
                    footer.showText(label);
                    footer.endText();
                }
            }
        }

        private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (String word : sanitize(value, font).split(" ")) {
                if (word.isEmpty()) continue;

                // break words that are wider than the line on their own
                while (widthOf(word, font, size) > maxWidth) {
                    int cut = Math.max(1, fitCount(word, font, size, maxWidth));
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }

                String candidate = current.isEmpty() ? word : current + " " + word;
                if (widthOf(candidate, font, size) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
            if (lines.isEmpty()) lines.add("");
            return lines;
        }

        private int fitCount(String word, PDFont font, float size, float maxWidth) throws IOException {
            int count = 0;
            for (int i = 1; i <= word.length(); i++) {
                if (widthOf(word.substring(0, i), font, size) > maxWidth) break;
                count = i;
            }
            return count;
        }

        private float widthOf(String value, PDFont font, float size) throws IOException {
            return font.getStringWidth(value) / 1000f * size;
        }

        private String sanitize(String value, PDFont font) {
            if (value == null) return "";
            StringBuilder out = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); ) {
                int cp = value.codePointAt(i);
                i += Character.charCount(cp);

                if (Character.isWhitespace(cp) || Character.isISOControl(cp)) {
                    out.append(' ');
                    continue;
                }
                String s = new String(Character.toChars(cp));
                try {
                    font.encode(s);
                    out.append(s);
                } catch (IOException | IllegalArgumentException ex) {
                    out.append('?');
                }
            }
            return out.toString();
        }
    }
}