package com.learningLadder.learningLadder.utils.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Minimal text layout on PDFBox: wrapping, page breaks, and safe handling of unsupported characters. */
public final class PdfTextWriter {

    private static final float MARGIN = 50f;

    public final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    public final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final PDDocument doc;
    private final float usableWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private PDPageContentStream stream;
    private float y;

    public PdfTextWriter(PDDocument doc) throws IOException {
        this.doc = doc;
        newPage();
    }

    public void gap(float points) throws IOException {
        if (y - points < MARGIN) newPage(); else y -= points;
    }

    public void field(String label, String value) throws IOException {
        if (value == null || value.isBlank()) return;
        line(label + ": " + value, regular, 11);
    }

    public void section(String title, String text) throws IOException {
        if (text == null || text.isBlank()) return;
        gap(10);
        line(title, bold, 13);
        line(text, regular, 11);
    }

    public void line(String text, PDType1Font font, float size) throws IOException {
        float leading = size * 1.4f;
        for (String paragraph : text.split("\\R", -1)) {
            for (String wrapped : wrap(sanitize(paragraph, font), font, size)) {
                if (y - leading < MARGIN) newPage();
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN, y - size);
                stream.showText(wrapped);
                stream.endText();
                y -= leading;
            }
        }
    }

    public void finish() throws IOException {
        stream.close();
    }

    private void newPage() throws IOException {
        if (stream != null) stream.close();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        stream = new PDPageContentStream(doc, page);
        y = PDRectangle.A4.getHeight() - MARGIN;
    }

    private String sanitize(String text, PDType1Font font) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            if (ch == '\t') { sb.append(' '); continue; }
            try {
                font.encode(String.valueOf(ch));
                sb.append(ch);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private List<String> wrap(String text, PDType1Font font, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(candidate, font, size) <= usableWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            String rest = word;
            while (width(rest, font, size) > usableWidth) {
                int cut = rest.length();
                while (cut > 1 && width(rest.substring(0, cut), font, size) > usableWidth) cut--;
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            current.append(rest);
        }
        lines.add(current.toString());
        return lines;
    }

    private float width(String s, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(s) / 1000f * size;
    }
}