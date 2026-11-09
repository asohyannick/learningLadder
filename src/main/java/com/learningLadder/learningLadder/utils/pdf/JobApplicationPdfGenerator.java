package com.learningLadder.learningLadder.utils.pdf;

import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationResponseDto;
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
import java.util.List;

@Component
public class JobApplicationPdfGenerator {

    private static final PDRectangle PAGE_SIZE =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 36f;
    private static final float FOOTER_SPACE = 24f;
    private static final float ROW_HEIGHT = 18f;
    private static final float CELL_PADDING = 4f;
    private static final float FONT_SIZE = 8.5f;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private static final String[] HEADERS =
            {"#", "Full name", "Email", "Phone", "Country", "Current job title", "Exp. (yrs)", "Status", "Submitted (UTC)"};
    private static final float[] COLUMN_WIDTHS =
            {28, 120, 160, 80, 70, 110, 50, 70, 82};
    private static final float TABLE_WIDTH = PAGE_SIZE.getWidth() - 2 * MARGIN;

    public byte[] generate(List<JobApplicationResponseDto> applications, long totalMatching, String filterSummary) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PDPageContentStream cs = newPage(document);
            float y = PAGE_SIZE.getHeight() - MARGIN;
            y = drawTitleBlock(cs, regular, bold, y, applications.size(), totalMatching, filterSummary);
            y = drawTableHeader(cs, bold, y);

            int index = 1;
            for (JobApplicationResponseDto application : applications) {
                if (y - ROW_HEIGHT < MARGIN + FOOTER_SPACE) {
                    cs.close();
                    cs = newPage(document);
                    y = drawTableHeader(cs, bold, PAGE_SIZE.getHeight() - MARGIN);
                }
                drawRow(cs, regular, y, index % 2 == 0, new String[]{
                        String.valueOf(index),
                        application.fullName(),
                        application.email(),
                        application.phoneNumber(),
                        application.country(),
                        application.currentJobTitle(),
                        application.yearsOfExperience() == null ? null : String.valueOf(application.yearsOfExperience()),
                        application.status() == null ? null : application.status().name(),
                        application.submittedAt() == null ? null : DATE_FORMAT.format(application.submittedAt())
                });
                y -= ROW_HEIGHT;
                index++;
            }

            if (applications.isEmpty()) {
                cs.setNonStrokingColor(0.3f, 0.3f, 0.3f);
                drawText(cs, regular, FONT_SIZE + 1, MARGIN, y - 16,
                        "No job applications match the selected filters.");
            }
            cs.close();

            addFooters(document, regular);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate job applications PDF", ex);
        }
    }

    private PDPageContentStream newPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        return new PDPageContentStream(document, page);
    }

    private float drawTitleBlock(PDPageContentStream cs, PDFont regular, PDFont bold, float y,
                                 int shown, long totalMatching, String filterSummary) throws IOException {
        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
        drawText(cs, bold, 16, MARGIN, y - 16, "Job Applications Report");

        cs.setNonStrokingColor(0.35f, 0.35f, 0.35f);
        String meta = "Generated " + DATE_FORMAT.format(Instant.now()) + " UTC  |  Showing "
                + shown + " of " + totalMatching + " matching application(s)";
        drawText(cs, regular, 9, MARGIN, y - 32, fit(sanitize(meta, regular), regular, 9, TABLE_WIDTH));

        String filters = "Filters: " + (filterSummary == null || filterSummary.isBlank() ? "none" : filterSummary);
        drawText(cs, regular, 9, MARGIN, y - 45, fit(sanitize(filters, regular), regular, 9, TABLE_WIDTH));

        return y - 58;
    }

    private float drawTableHeader(PDPageContentStream cs, PDFont bold, float y) throws IOException {
        cs.setNonStrokingColor(0.17f, 0.24f, 0.36f);
        cs.addRect(MARGIN, y - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
        cs.fill();

        cs.setNonStrokingColor(1f, 1f, 1f);
        float x = MARGIN;
        for (int i = 0; i < HEADERS.length; i++) {
            String text = fit(HEADERS[i], bold, FONT_SIZE, COLUMN_WIDTHS[i] - 2 * CELL_PADDING);
            drawText(cs, bold, FONT_SIZE, x + CELL_PADDING, y - ROW_HEIGHT + 6, text);
            x += COLUMN_WIDTHS[i];
        }
        return y - ROW_HEIGHT;
    }

    private void drawRow(PDPageContentStream cs, PDFont font, float y, boolean shaded, String[] cells) throws IOException {
        if (shaded) {
            cs.setNonStrokingColor(0.95f, 0.96f, 0.98f);
            cs.addRect(MARGIN, y - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
            cs.fill();
        }

        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
        float x = MARGIN;
        for (int i = 0; i < cells.length; i++) {
            String text = fit(sanitize(cells[i], font), font, FONT_SIZE, COLUMN_WIDTHS[i] - 2 * CELL_PADDING);
            drawText(cs, font, FONT_SIZE, x + CELL_PADDING, y - ROW_HEIGHT + 6, text);
            x += COLUMN_WIDTHS[i];
        }

        cs.setStrokingColor(0.85f, 0.85f, 0.85f);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y - ROW_HEIGHT);
        cs.lineTo(MARGIN + TABLE_WIDTH, y - ROW_HEIGHT);
        cs.stroke();
    }

    private void addFooters(PDDocument document, PDFont font) throws IOException {
        int totalPages = document.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                drawText(cs, font, 8, MARGIN, MARGIN - 12, "Learning Ladder - Job Applications");
                String pageLabel = "Page " + (i + 1) + " of " + totalPages;
                float width = font.getStringWidth(pageLabel) / 1000 * 8;
                drawText(cs, font, 8, PAGE_SIZE.getWidth() - MARGIN - width, MARGIN - 12, pageLabel);
            }
        }
    }

    private void drawText(PDPageContentStream cs, PDFont font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private String fit(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (width(text, font, fontSize) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && width(text.substring(0, end) + ellipsis, font, fontSize) > maxWidth) {
            end--;
        }
        return end == 0 ? "" : text.substring(0, end) + ellipsis;
    }

    private float width(String text, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }


    private String sanitize(String value, PDFont font) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        value.replaceAll("[\\r\\n\\t]+", " ").codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            builder.append(canEncode(font, character) ? character : "?");
        });
        return builder.toString();
    }

    private boolean canEncode(PDFont font, String character) {
        try {
            font.encode(character);
            return true;
        } catch (IllegalArgumentException | IOException ex) {
            return false;
        }
    }
}