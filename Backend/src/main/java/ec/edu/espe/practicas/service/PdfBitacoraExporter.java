package ec.edu.espe.practicas.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

@Component
public class PdfBitacoraExporter implements BitacoraExporter {
    private static final System.Logger LOGGER = System.getLogger(PdfBitacoraExporter.class.getName());
    private static final float MARGIN = 34;
    private static final float BOTTOM = 34;
    private static final float TABLE_WIDTH = 774;
    private static final float HEADER_HEIGHT = 25;
    private static final float CELL_PADDING = 6;
    private static final float ROW_LINE_HEIGHT = 9.2f;
    private static final float[] COLUMN_WIDTHS = {72, 115, 282, 80, 112, 113};
    private static final String[] HEADERS = {"Fecha", "Practicante", "Actividad", "Horas aceptadas", "Entrada", "Salida"};
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final int GREEN_R = 60;
    private static final int GREEN_G = 126;
    private static final int GREEN_B = 64;

    @Override
    public BitacoraFormat format() {
        return BitacoraFormat.PDF;
    }

    @Override
    public byte[] export(BitacoraExportData data) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfPage page = firstPage(document, data);
            boolean alternate = false;

            if (data.activities().isEmpty()) {
                float rowHeight = 32;
                if (page.y - rowHeight < BOTTOM) {
                    page.close();
                    page = continuationPage(document);
                }
                drawEmptyRow(page.stream, page.y, rowHeight);
                page.y -= rowHeight;
            } else {
                for (BitacoraExportData.ActivityRow activity : data.activities()) {
                    List<List<String>> cells = rowCells(activity);
                    float rowHeight = rowHeight(cells);
                    if (page.y - rowHeight < BOTTOM) {
                        page.close();
                        page = continuationPage(document);
                        alternate = false;
                    }
                    drawRow(page.stream, page.y, rowHeight, cells, alternate);
                    page.y -= rowHeight;
                    alternate = !alternate;
                }
            }

            page.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "No se pudo generar el PDF de la bitácora.", exception);
            throw new IllegalStateException("No se pudo generar el PDF de la bitácora.", exception);
        }
    }

    private static PdfPage firstPage(PDDocument document, BitacoraExportData data) throws IOException {
        PdfPage page = createPage(document);
        float left = MARGIN;
        writeText(page.stream, BOLD, 17, left, 551, GREEN_R, GREEN_G, GREEN_B,
                "Bitácora de actividades aceptadas");
        writeText(page.stream, REGULAR, 9, left, 531, 91, 105, 95,
                "Practicante: " + pdfSafe(data.traineeName()));
        writeText(page.stream, REGULAR, 9, left, 515, 91, 105, 95,
                "Período: " + data.from().format(DISPLAY_DATE) + " a " + data.to().format(DISPLAY_DATE));

        float summaryY = 480;
        setFillColor(page.stream, 240, 246, 240);
        page.stream.addRect(left, summaryY, TABLE_WIDTH, 23);
        page.stream.fill();
        setFillColor(page.stream, GREEN_R, GREEN_G, GREEN_B);
        page.stream.addRect(left, summaryY, 3, 23);
        page.stream.fill();
        writeText(page.stream, BOLD, 9, left + 11, summaryY + 8,
                GREEN_R, GREEN_G, GREEN_B,
                "Total de horas aceptadas: " + data.acceptedHours().toPlainString());

        page.y = 448;
        drawTableHeader(page.stream, page.y);
        page.y -= HEADER_HEIGHT;
        return page;
    }

    private static PdfPage continuationPage(PDDocument document) throws IOException {
        PdfPage page = createPage(document);
        writeText(page.stream, BOLD, 12, MARGIN, 553, GREEN_R, GREEN_G, GREEN_B,
                "Bitácora de actividades aceptadas - continuación");
        page.y = 523;
        drawTableHeader(page.stream, page.y);
        page.y -= HEADER_HEIGHT;
        return page;
    }

    private static PdfPage createPage(PDDocument document) throws IOException {
        PDRectangle a4 = PDRectangle.A4;
        PDPage page = new PDPage(new PDRectangle(a4.getHeight(), a4.getWidth()));
        document.addPage(page);
        return new PdfPage(new PDPageContentStream(document, page));
    }

    private static void drawTableHeader(PDPageContentStream stream, float top) throws IOException {
        float x = MARGIN;
        float bottom = top - HEADER_HEIGHT;
        setFillColor(stream, GREEN_R, GREEN_G, GREEN_B);
        stream.addRect(MARGIN, bottom, TABLE_WIDTH, HEADER_HEIGHT);
        stream.fill();

        for (int column = 0; column < HEADERS.length; column++) {
            writeText(stream, BOLD, 8, x + CELL_PADDING, bottom + 9,
                    255, 255, 255, HEADERS[column]);
            x += COLUMN_WIDTHS[column];
        }
    }

    private static List<List<String>> rowCells(BitacoraExportData.ActivityRow activity) throws IOException {
        String[] values = {
                activity.date().format(DISPLAY_DATE),
                pdfSafe(activity.traineeName()),
                pdfSafe(activity.description()),
                activity.hours().stripTrailingZeros().toPlainString(),
                pdfSafe(activity.checkIn()),
                pdfSafe(activity.checkOut())
        };
        List<List<String>> result = new ArrayList<>(values.length);
        for (int column = 0; column < values.length; column++) {
            result.add(wrap(values[column], COLUMN_WIDTHS[column] - CELL_PADDING * 2,
                    column == 2 ? 7.5f : 7.1f, REGULAR));
        }
        return result;
    }

    private static float rowHeight(List<List<String>> cells) {
        int lineCount = cells.stream().mapToInt(List::size).max().orElse(1);
        return Math.max(25, lineCount * ROW_LINE_HEIGHT + 9);
    }

    private static void drawRow(
            PDPageContentStream stream, float top, float height, List<List<String>> cells, boolean alternate)
            throws IOException {
        float bottom = top - height;
        setFillColor(stream, alternate ? 244 : 255, alternate ? 248 : 255, alternate ? 244 : 255);
        stream.addRect(MARGIN, bottom, TABLE_WIDTH, height);
        stream.fill();

        setStrokeColor(stream, 221, 229, 222);
        stream.setLineWidth(0.45f);
        stream.addRect(MARGIN, bottom, TABLE_WIDTH, height);
        stream.stroke();

        float x = MARGIN;
        for (int column = 0; column < cells.size(); column++) {
            List<String> lines = cells.get(column);
            float baseline = top - 13;
            float fontSize = column == 2 ? 7.5f : 7.1f;
            for (String line : lines) {
                writeText(stream, REGULAR, fontSize, x + CELL_PADDING, baseline, 52, 64, 55, line);
                baseline -= ROW_LINE_HEIGHT;
            }
            x += COLUMN_WIDTHS[column];
        }

        setStrokeColor(stream, 221, 229, 222);
        stream.setLineWidth(0.45f);
        x = MARGIN;
        for (int column = 0; column < COLUMN_WIDTHS.length - 1; column++) {
            x += COLUMN_WIDTHS[column];
            stream.moveTo(x, bottom);
            stream.lineTo(x, top);
        }
        stream.stroke();
    }

    private static void drawEmptyRow(PDPageContentStream stream, float top, float height) throws IOException {
        float bottom = top - height;
        setFillColor(stream, 249, 251, 249);
        stream.addRect(MARGIN, bottom, TABLE_WIDTH, height);
        stream.fill();
        setStrokeColor(stream, 221, 229, 222);
        stream.addRect(MARGIN, bottom, TABLE_WIDTH, height);
        stream.stroke();
        writeText(stream, REGULAR, 8, MARGIN + 10, bottom + 12, 91, 105, 95,
                "No hay actividades aceptadas en este período.");
    }

    private static List<String> wrap(String value, float maxWidth, float fontSize, PDType1Font font)
            throws IOException {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (fontWidth(font, fontSize, word) > maxWidth) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                StringBuilder chunk = new StringBuilder();
                for (char character : word.toCharArray()) {
                    String candidate = chunk.toString() + character;
                    if (!chunk.isEmpty() && fontWidth(font, fontSize, candidate) > maxWidth) {
                        lines.add(chunk.toString());
                        chunk.setLength(0);
                    }
                    chunk.append(character);
                }
                if (!chunk.isEmpty()) line.append(chunk);
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fontWidth(font, fontSize, candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static float fontWidth(PDType1Font font, float fontSize, String value) throws IOException {
        return font.getStringWidth(value) * fontSize / 1000;
    }

    private static void writeText(PDPageContentStream stream, PDType1Font font, float size,
            float x, float y, int red, int green, int blue, String value) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        setFillColor(stream, red, green, blue);
        stream.newLineAtOffset(x, y);
        stream.showText(pdfSafe(value));
        stream.endText();
    }

    private static String pdfSafe(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder safe = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                safe.append(' ');
            } else if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                safe.append('?');
            } else if (codePoint >= 32) {
                String glyph = new String(Character.toChars(codePoint));
                try {
                    REGULAR.getStringWidth(glyph);
                    safe.append(glyph);
                } catch (IOException | IllegalArgumentException unsupportedGlyph) {
                    safe.append('?');
                }
            }
        });
        return safe.toString();
    }

    private static void setFillColor(PDPageContentStream stream, int red, int green, int blue)
            throws IOException {
        stream.setNonStrokingColor(red / 255f, green / 255f, blue / 255f);
    }

    private static void setStrokeColor(PDPageContentStream stream, int red, int green, int blue)
            throws IOException {
        stream.setStrokingColor(red / 255f, green / 255f, blue / 255f);
    }

    private static final class PdfPage {
        private final PDPageContentStream stream;
        private float y;

        private PdfPage(PDPageContentStream stream) {
            this.stream = stream;
        }

        private void close() throws IOException {
            stream.close();
        }
    }
}
