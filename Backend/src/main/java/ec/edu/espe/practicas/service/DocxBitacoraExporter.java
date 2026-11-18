package ec.edu.espe.practicas.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

@Component
public class DocxBitacoraExporter implements BitacoraExporter {
    @Override public BitacoraFormat format() { return BitacoraFormat.DOCX; }

    @Override
    public byte[] export(BitacoraExportData data) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText("Bitácora de actividades aceptadas");
            document.createParagraph().createRun().setText("Practicante: " + data.traineeName());
            document.createParagraph().createRun().setText("Período: " + data.from() + " a " + data.to());
            document.createParagraph().createRun().setText("Total de horas aceptadas: " + data.acceptedHours().toPlainString());

            XWPFTable table = document.createTable(data.activities().size() + 1, 6);
            String[] headers = {"Fecha", "Practicante", "Actividad", "Horas aceptadas", "Entrada", "Salida"};
            for (int col = 0; col < headers.length; col++) {
                XWPFRun run = table.getRow(0).getCell(col).getParagraphs().get(0).createRun();
                run.setBold(true);
                run.setText(headers[col]);
            }
            for (int i = 0; i < data.activities().size(); i++) {
                BitacoraExportData.ActivityRow activity = data.activities().get(i);
                String[] values = {activity.date().toString(), activity.traineeName(), activity.description(),
                        activity.hours().toPlainString(), activity.checkIn(), activity.checkOut()};
                for (int col = 0; col < values.length; col++) {
                    table.getRow(i + 1).getCell(col).setText(values[col] == null ? "" : values[col]);
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el archivo DOCX de la bitácora.", exception);
        }
    }
}
